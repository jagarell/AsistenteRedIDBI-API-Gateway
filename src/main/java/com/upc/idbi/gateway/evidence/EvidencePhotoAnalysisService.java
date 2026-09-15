package com.upc.idbi.gateway.evidence;

import com.upc.idbi.gateway.evidence.dto.PhotoAnalyzeRequest;
import com.upc.idbi.gateway.evidence.dto.PhotoAnalyzeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/** Envía la foto (base64) al motor de visión IA en FastAPI y devuelve la
 * descripción (marca/modelo/observaciones) que identifica en la imagen. */
@Service
@RequiredArgsConstructor
public class EvidencePhotoAnalysisService {

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public PhotoAnalyzeResult analyze(String category, byte[] imageBytes) {
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        PhotoAnalyzeRequest request = new PhotoAnalyzeRequest(category, imageBase64);

        PhotoAnalyzeResult result = restTemplate.postForObject(
                fastApiBaseUrl + "/analyze-photo",
                request,
                PhotoAnalyzeResult.class
        );

        return result != null ? result : new PhotoAnalyzeResult("No se pudo analizar la foto.", null, null);
    }
}
