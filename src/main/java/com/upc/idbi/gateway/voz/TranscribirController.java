package com.upc.idbi.gateway.voz;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Dictado por voz: la app y la web graban el audio y el servidor lo transcribe (el reconocimiento del teléfono fallaba con
 * los términos del oficio). Devuelve {@code {text, error}}: si no se pudo transcribir, {@code text} viene vacío y {@code error}
 * explica por qué, para mostrarlo tal cual.
 */
@RestController
@RequiredArgsConstructor
public class TranscribirController {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    @PostMapping(value = "/api/transcribir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SuppressWarnings("unchecked")
    public Map<String, Object> transcribir(@RequestPart("audio") MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return error("No llegó el audio. Vuelve a grabarlo.");
        }
        if (audio.getSize() > MAX_BYTES) {
            return error("El audio es muy largo. Graba un mensaje más corto.");
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException ex) {
            return error("No se pudo leer el audio. Vuelve a grabarlo.");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("audioBase64", Base64.getEncoder().encodeToString(bytes));
        String mime = audio.getContentType();
        body.put("mime", mime == null || mime.isBlank() ? "audio/mp4" : mime);
        try {
            Map<String, Object> out = restTemplate.postForObject(fastApiBaseUrl + "/transcribir", body, Map.class);
            return out != null ? out : error("No se pudo transcribir el audio. Inténtalo de nuevo.");
        } catch (RuntimeException ex) {
            return error("No se pudo transcribir el audio. Revisa tu conexión e inténtalo de nuevo.");
        }
    }

    private static Map<String, Object> error(String mensaje) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", "");
        m.put("error", mensaje);
        return m;
    }
}
