package com.upc.idbi.gateway.analysis;

import com.upc.idbi.gateway.analysis.dto.AnalysisResponse;
import com.upc.idbi.gateway.analysis.dto.AnalyzeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final RestTemplate restTemplate;

    public AnalysisResponse analyze(Long evaluationId) {
        AnalyzeRequest request = new AnalyzeRequest(
                evaluationId,
                "Restaurante El Rincón",
                3
        );

        return restTemplate.postForObject(
                "http://localhost:8000/analyze",
                request,
                AnalysisResponse.class
        );
    }
}