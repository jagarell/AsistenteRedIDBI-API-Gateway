package com.upc.idbi.gateway.evidence;

import com.upc.idbi.gateway.evidence.dto.PhotoAnalyzeRequest;
import com.upc.idbi.gateway.evidence.dto.PhotoAnalyzeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/** Envía la foto (base64) al motor de visión IA en FastAPI y devuelve la
 * descripción (marca/modelo/observaciones) que identifica en la imagen. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidencePhotoAnalysisService {

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    /** Nunca lanza: si FastAPI no responde (caído, timeout, red), la foto
     * igual debe guardarse — solo se pierde el análisis de IA, no la
     * evidencia. Antes esta llamada no tenía try/catch y una falla acá
     * tumbaba la subida completa de la foto. */
    public PhotoAnalyzeResult analyze(String category, byte[] imageBytes) {
        try {
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            PhotoAnalyzeRequest request = new PhotoAnalyzeRequest(category, imageBase64);

            PhotoAnalyzeResult result = restTemplate.postForObject(
                    fastApiBaseUrl + "/analyze-photo",
                    request,
                    PhotoAnalyzeResult.class
            );

            return result != null ? result : new PhotoAnalyzeResult("No se pudo analizar la foto.", null, null);
        } catch (RestClientException ex) {
            log.warn("No se pudo analizar la foto con IA (categoría {}); se guarda igual sin análisis", category, ex);
            return new PhotoAnalyzeResult(
                    "No se pudo analizar la foto en este momento (servicio de IA no disponible).",
                    null,
                    null
            );
        }
    }
}
