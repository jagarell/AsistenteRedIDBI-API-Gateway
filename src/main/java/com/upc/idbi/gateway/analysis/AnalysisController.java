package com.upc.idbi.gateway.analysis;

import com.upc.idbi.gateway.analysis.dto.AnalyzeAnswersRequest;
import com.upc.idbi.gateway.analysis.dto.AnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    public AnalysisResponse analyze(
            @PathVariable Long evaluationId,
            @RequestBody(required = false) AnalyzeAnswersRequest request
    ) {
        Map<String, String> answers = request != null ? request.answers() : null;
        return analysisService.analyze(evaluationId, answers);
    }

    @GetMapping
    public AnalysisResponse getAnalysis(@PathVariable Long evaluationId) {
        return analysisService.analyze(evaluationId, null);
    }
}