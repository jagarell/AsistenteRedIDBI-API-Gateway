package com.upc.idbi.gateway.history;

import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evaluation.EvaluationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final EvaluationRepository repository;
    private final com.upc.idbi.gateway.evaluation.EvaluationEstadoService estadoService;

    @GetMapping
    public List<Evaluation> getHistory(@RequestParam(required = false) EvaluationStatus status) {
        if (status != null) {
            return estadoService.decorate(repository.findByStatus(status));
        }

        return estadoService.decorate(repository.findActive());
    }

    @GetMapping("/{id}")
    public Evaluation getHistoryDetail(@PathVariable Long id) {
        return estadoService.decorate(repository.findById(id).orElseThrow());
    }
}