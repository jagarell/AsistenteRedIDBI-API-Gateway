package com.upc.idbi.gateway.analysis;

import com.upc.idbi.gateway.analysis.dto.AnalysisResponse;
import com.upc.idbi.gateway.analysis.dto.AnalyzeRequest;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final RestTemplate restTemplate;
    private final EvaluationRepository evaluationRepository;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public AnalysisResponse analyze(Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la evaluación con id " + evaluationId
                ));

        // Se envían los datos reales de la evaluación, no un payload ficticio.
        AnalyzeRequest request = new AnalyzeRequest(
                evaluation.getId(),
                evaluation.getRestaurantName(),
                evaluation.getProgress()
        );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/analyze",
                request,
                AnalysisResponse.class
        );
    }
}
