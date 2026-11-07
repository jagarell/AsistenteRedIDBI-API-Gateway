package com.upc.idbi.gateway.evaluation;

import java.util.Map;
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
    private final EvaluationAnnulService annulService;
    private final EvaluationEstadoService estadoService;

    @GetMapping
    public List<Evaluation> getAll() {
        return estadoService.decorate(repository.findActive());
    }

    @GetMapping("/{id}")
    public Evaluation getById(@PathVariable Long id) {
        return estadoService.decorate(repository.findById(id).orElseThrow());
    }

    @PostMapping
    public Evaluation createEvaluation(@RequestBody(required = false) Evaluation body,
                                       @org.springframework.security.core.annotation.AuthenticationPrincipal com.upc.idbi.gateway.security.AuthenticatedUser user) {
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
                // Una evaluación nueva es un borrador hasta que se completa.
                .status(EvaluationStatus.BORRADOR)
                .progress(0)
                .score(0)
                .createdAt(LocalDateTime.now())
                .creadoPorId(user == null ? null : user.id())
                .creadoPorNombre(user == null ? null : user.fullName())
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

    /**
     * Ficha de solo lectura de una evaluación que se quedó en el levantamiento (Borrador · sin terminar): avance y lo que ya se levantó.
     * No tiene puntaje, no cuenta en «Por validar» y no se puede compartir (doc 0.0.7 §19).
     */
    @GetMapping("/{id}/levantamiento")
    public java.util.Map<String, Object> levantamiento(@PathVariable Long id) {
        Evaluation e = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("No existe la evaluación con id " + id));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("evaluationId", e.getId());
        m.put("local", e.getRestaurantName());
        m.put("tecnico", e.getTechnicianName());
        m.put("avance", e.getProgress() == null ? 0 : e.getProgress());
        m.put("direccion", e.getAddress());
        m.put("contacto", e.getContactName());
        m.put("telefono", e.getPhone());
        m.put("rubro", e.getEstablishmentType());
        m.put("creada", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        m.put("conMinuta", e.getMinutaId() != null);
        return m;
    }

    /** Anula una evaluación en borrador (botón del Historial). */
    @PostMapping("/{id}/anular")
    public Evaluation annul(@PathVariable Long id) {
        return annulService.annul(id);
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