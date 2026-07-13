package com.upc.idbi.gateway.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EvaluationController {

    private final EvaluationRepository repository;

    @GetMapping
    public List<Evaluation> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Evaluation getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow();
    }

    @PostMapping
    public Evaluation createEvaluation(@RequestBody(required = false) Evaluation body) {
        Evaluation evaluation = Evaluation.builder()
                .restaurantName(body != null && body.getRestaurantName() != null ? body.getRestaurantName() : "Nueva Evaluación")
                .location(body != null && body.getLocation() != null ? body.getLocation() : "Lima · Perú")
                .address(body != null ? body.getAddress() : null)
                .contactName(body != null ? body.getContactName() : null)
                .contactEmail(body != null ? body.getContactEmail() : null)
                .phone(body != null ? body.getPhone() : null)
                .status(EvaluationStatus.EN_ANALISIS)
                .progress(0)
                .score(0)
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(evaluation);
    }

    @PostMapping("/{id}/analyze")
    public Evaluation analyze(@PathVariable Long id) {
        Evaluation evaluation = repository.findById(id).orElseThrow();
        evaluation.setStatus(EvaluationStatus.COMPLETADO);
        evaluation.setProgress(72);
        evaluation.setScore(72);
        return repository.save(evaluation);
    }

    @PutMapping("/{id}")
    public Evaluation updateEvaluation(
            @PathVariable Long id,
            @RequestBody Evaluation body
    ) {
        Evaluation evaluation = repository.findById(id).orElseThrow();

        evaluation.setRestaurantName(body.getRestaurantName());
        evaluation.setLocation(body.getLocation());
        evaluation.setAddress(body.getAddress());
        evaluation.setContactName(body.getContactName());
        evaluation.setPhone(body.getPhone());
        evaluation.setContactEmail(body.getContactEmail());
        evaluation.setProgress(body.getProgress());
        evaluation.setScore(body.getScore());
        evaluation.setStatus(body.getStatus());

        return repository.save(evaluation);
    }

    @DeleteMapping("/{id}")
    public void deleteEvaluation(@PathVariable Long id) {
        repository.deleteById(id);
    }

}