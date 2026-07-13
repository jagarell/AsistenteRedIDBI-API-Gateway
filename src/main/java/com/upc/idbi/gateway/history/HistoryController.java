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
@CrossOrigin(origins = "*")
public class HistoryController {

    private final EvaluationRepository repository;

    @GetMapping
    public List<Evaluation> getHistory(@RequestParam(required = false) EvaluationStatus status) {
        if (status != null) {
            return repository.findByStatus(status);
        }

        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Evaluation getHistoryDetail(@PathVariable Long id) {
        return repository.findById(id).orElseThrow();
    }
}