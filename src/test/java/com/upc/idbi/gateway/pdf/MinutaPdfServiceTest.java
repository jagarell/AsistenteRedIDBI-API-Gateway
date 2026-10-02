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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Genera la minuta del caso Rock &amp; Burgers (documento armado por FastAPI a
 * partir del chat completo + las fotos reales de la minuta de ejemplo) y
 * verifica su contenido. El PDF queda en target/minuta-rock-burgers.pdf para
 * compararlo a ojo con el ejemplo del cliente.
 */
class MinutaPdfServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void generaLaMinutaDeRockAndBurgers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> document;
        try (InputStream in = getClass().getResourceAsStream("/minuta/rock_burgers_document.json")) {
            document = mapper.readValue(in, new TypeReference<>() {});
        }

        EvidenceStorageService storage = mock(EvidenceStorageService.class);
        Map<String, byte[]> files = new HashMap<>();
        when(storage.read(anyLong(), anyString())).thenAnswer(inv -> files.get(inv.<String>getArgument(1)));
        List<Evidence> evidences = new ArrayList<>();
        long id = 0;
        for (Map<String, Object> annex : (List<Map<String, Object>>) document.get("annexA")) {
            String code = (String) annex.get("code");
            String scope = (String) annex.get("scope");
            String file = "E5".equals(code) ? "E5-" + scope.substring(scope.length() - 1) : code;
            String resource = "/minuta/" + file + ".jpg";
            if (getClass().getResource(resource) == null) {
                continue;
            }
            byte[] bytes;
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                bytes = in.readAllBytes();
            }
            String stored = "f" + (++id) + ".jpg";
            files.put(stored, bytes);
            evidences.add(Evidence.builder().id(id).evaluationId(1L).storedFileName(stored)
                    .evidenceCode(code).chatScope(scope).build());
        }

        MinutaPdfService service = new MinutaPdfService(null, null, storage, null, mapper);
        byte[] pdf = service.render(document, evidences, 1L);

        Path out = Path.of("target", "minuta-rock-burgers.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);

        try (PDDocument pd = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(pd);
            assertThat(pd.getNumberOfPages()).isBetween(4, 8);
            assertThat(text).contains("MINUTA TÉCNICA DE RED", "Validación técnica automática",
                    "Anexo A.", "Resumen ejecutivo", "OBSERVADO", "Impresoras", "Rock & Burgers");
        }
    }
}
