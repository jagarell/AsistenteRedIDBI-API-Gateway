package com.upc.idbi.gateway.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidenceStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Minuta del simulador (documento en bloques): contenido, anexo con fotos y marca de agua en cada hoja. */
class MinutaPdfV2Test {

    @SuppressWarnings("unchecked")
    private byte[] pdf(String watermark) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> document;
        try (InputStream in = getClass().getResourceAsStream("/minuta/restobar_document.json")) {
            document = mapper.readValue(in, new TypeReference<>() {});
        }
        byte[] sample;
        try (InputStream in = getClass().getResourceAsStream("/minuta/sample.jpg")) {
            sample = in.readAllBytes();
        }
        EvidenceStorageService storage = mock(EvidenceStorageService.class);
        when(storage.read(anyLong(), anyString())).thenReturn(sample);
        List<Evidence> evidences = new ArrayList<>();
        long id = 0;
        for (Map<String, Object> block : (List<Map<String, Object>>) document.get("blocks")) {
            for (Map<String, Object> cell : (List<Map<String, Object>>) block.getOrDefault("cells", List.of())) {
                for (Map<String, Object> ref : (List<Map<String, Object>>) cell.get("evidence")) {
                    evidences.add(Evidence.builder().id(++id).evaluationId(1L).storedFileName("f" + id + ".jpg")
                            .evidenceCode((String) ref.get("code")).chatScope((String) ref.get("scope")).build());
                }
            }
        }
        MinutaPdfService service = new MinutaPdfService(null, null, storage, null, mapper);
        return service.render(document, evidences, 1L, watermark);
    }

    @Test
    void generaLaMinutaNuevaConMarcaDeAgua() throws Exception {
        byte[] bytes = pdf("SIN VALIDAR");
        Files.write(Path.of("target", "minuta-restobar.pdf"), bytes);
        try (PDDocument pd = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(pd);
            assertThat(pd.getNumberOfPages()).isGreaterThan(3);
            assertThat(text).contains("MINUTA TÉCNICA DE RED", "Datos generales", "Antecedentes", "Topología de red",
                    "Validación técnica automática", "Resumen ejecutivo", "Aviso de viabilidad", "Pasos a seguir",
                    "Anexo A.", "No verificada");
            // la marca de agua (texto en diagonal) se dibuja en cada hoja: el stream de cada página la incluye
            for (var page : pd.getPages()) {
                try (var in = page.getContents()) {
                    assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1)).contains("SIN VALIDAR");
                }
            }
        }
    }

    @Test
    void sinMarcaDeAguaCuandoEstaValidada() throws Exception {
        try (PDDocument pd = PDDocument.load(pdf(null))) {
            assertThat(new PDFTextStripper().getText(pd)).doesNotContain("VISTA PREVIA");
        }
    }
}
