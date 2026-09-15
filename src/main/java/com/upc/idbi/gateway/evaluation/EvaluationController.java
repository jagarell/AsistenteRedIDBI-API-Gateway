package com.upc.idbi.gateway.evaluation;

import com.upc.idbi.gateway.validation.RucValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationRepository repository;
    private final RucValidationService rucValidationService;

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
        // Solo se valida el formato cuando el cliente manda un nombre real —
        // el placeholder "Nueva Evaluación" (evaluación recién creada, antes
        // de que el chat pregunte el nombre del local) no pasa por acá.
        if (body != null && body.getRestaurantName() != null) {
            validateBusinessNameFormat(body.getRestaurantName());
        }

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
        validateBusinessNameFormat(body.getRestaurantName());

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

    private void validateBusinessNameFormat(String restaurantName) {
        var format = rucValidationService.hasValidFormat(restaurantName);
        if (!format.valid()) {
            throw new IllegalArgumentException(format.reason());
        }
    }

}