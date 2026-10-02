package com.upc.idbi.gateway.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidenceRepository;
import com.upc.idbi.gateway.evidence.EvidenceStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Minuta técnica en PDF. El contenido (secciones, validación técnica, mapa,
 * resumen) lo arma FastAPI a partir del estado del chat
 * (POST /chat/minuta-document); acá solo se le agregan las fotos guardadas de
 * cada evidencia y se compone el documento con una plantilla HTML/CSS
 * (templates/minuta.html) renderizada por OpenHTMLtoPDF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinutaPdfService {

    private static final String STATE_KEY = "__state";
    private static final int ANNEX_IMAGE_MAX_WIDTH = 520;

    private final EvaluationRepository evaluationRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageService storageService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    private final TemplateEngine templateEngine = buildTemplateEngine();

    public byte[] generate(Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación " + evaluationId));
        String stateJson = stateOf(evaluation);

        @SuppressWarnings("unchecked")
        Map<String, Object> document = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/minuta-document",
                Map.of("state", stateJson),
                Map.class
        );
        if (document == null) {
            throw new IllegalStateException("El motor no devolvió el documento de la minuta");
        }

        List<Evidence> evidences =
                evidenceRepository.findByEvaluationIdAndEvidenceCodeIsNotNullOrderByUploadedAtAsc(evaluationId);
        return render(document, evidences, evaluationId);
    }

    /** Renderiza un documento ya armado (también lo usan los tests). */
    public byte[] render(Map<String, Object> document, List<Evidence> evidences, Long evaluationId) {
        Context ctx = new Context();
        ctx.setVariable("doc", document);
        ctx.setVariable("logo", dataUri(resource("/pdf/logo_idbi.jpg"), "image/jpeg"));
        String map = (String) document.get("mapPngBase64");
        ctx.setVariable("mapImage", map == null || map.isBlank() ? null : "data:image/png;base64," + map);
        ctx.setVariable("annexImages", annexImages(document, evidences, evaluationId));

        String html = templateEngine.process("minuta", ctx);
        return toPdf(html);
    }

    // ---- internos -----------------------------------------------------------
    @SuppressWarnings("unchecked")
    private List<List<String>> annexImages(Map<String, Object> document, List<Evidence> evidences, Long evaluationId) {
        List<List<String>> result = new ArrayList<>();
        List<Map<String, Object>> annex = (List<Map<String, Object>>) document.getOrDefault("annexA", List.of());
        for (Map<String, Object> item : annex) {
            List<String> images = new ArrayList<>();
            for (Evidence e : evidences) {
                if (item.get("code").equals(e.getEvidenceCode())
                        && String.valueOf(item.get("scope")).equals(e.getChatScope() == null ? "" : e.getChatScope())) {
                    try {
                        images.add(dataUri(downscale(storageService.read(evaluationId, e.getStoredFileName())),
                                "image/jpeg"));
                    } catch (Exception ex) {
                        log.warn("No se pudo leer la imagen de evidencia {}", e.getId(), ex);
                    }
                }
            }
            result.add(images);
        }
        return result;
    }

    private String stateOf(Evaluation evaluation) {
        try {
            if (evaluation.getChatAnswersJson() == null || evaluation.getChatAnswersJson().isBlank()) {
                throw new IllegalArgumentException("El chat de esta evaluación aún no se completó");
            }
            Map<String, String> answers = objectMapper.readValue(
                    evaluation.getChatAnswersJson(), new TypeReference<Map<String, String>>() {});
            String state = answers.get(STATE_KEY);
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException(
                        "Esta evaluación se hizo con el flujo anterior y no tiene datos para la minuta");
            }
            return state;
        } catch (IOException e) {
            throw new IllegalStateException("Respuestas del chat inválidas", e);
        }
    }

    private byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la minuta", e);
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> resource("/pdf/fonts/DejaVuSansCondensed.ttf"), "Minuta",
                400, PdfRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> resource("/pdf/fonts/DejaVuSansCondensed-Bold.ttf"), "Minuta",
                700, PdfRendererBuilder.FontStyle.NORMAL, true);
        // Símbolos (✓ ⚠) que la fuente condensada no trae.
        builder.useFont(() -> resource("/pdf/fonts/DejaVuSans.ttf"), "Symbols",
                400, PdfRendererBuilder.FontStyle.NORMAL, true);
    }

    private static InputStream resource(String path) {
        InputStream in = MinutaPdfService.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Falta el recurso " + path);
        }
        return in;
    }

    private static String dataUri(InputStream in, String mime) {
        try (in) {
            return dataUri(in.readAllBytes(), mime);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dataUri(byte[] bytes, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** Reduce la foto para que el PDF no pese decenas de MB (las fotos quedan a ~170 px en la hoja). */
    private static byte[] downscale(byte[] original) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
        if (src == null) {
            return original;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w > ANNEX_IMAGE_MAX_WIDTH) {
            h = Math.max(1, h * ANNEX_IMAGE_MAX_WIDTH / w);
            w = ANNEX_IMAGE_MAX_WIDTH;
        }
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.8f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static TemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
