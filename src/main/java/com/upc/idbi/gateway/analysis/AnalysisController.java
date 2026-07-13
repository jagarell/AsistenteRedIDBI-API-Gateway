package com.upc.idbi.gateway.analysis;

import com.upc.idbi.gateway.analysis.dto.AnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    public AnalysisResponse analyze(@PathVariable Long evaluationId) {
        return analysisService.analyze(evaluationId);
    }

    @GetMapping
    public AnalysisResponse getAnalysis(@PathVariable Long evaluationId) {
        return analysisService.analyze(evaluationId);
    }
}