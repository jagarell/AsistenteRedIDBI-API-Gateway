package com.upc.idbi.gateway.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.email.EmailService;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaService;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.minuta.dto.MinutaResponse;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Minuta técnica de una evaluación en PDF (generada en el momento a partir de
 * las respuestas y evidencias del chat; no se almacena).
 *
 * <ul>
 *     <li>{@code ?preview=true}: vista previa con la marca "VISTA PREVIA"; no cambia ningún estado.</li>
 *     <li>Sin {@code preview}: si el PDF se genera bien, la minuta pasa de BORRADOR a COMPLETA (y se avisa a los
 *     supervisores). Si no se puede generar (por ejemplo falta una foto obligatoria) queda en BORRADOR.</li>
 *     <li>Si la minuta no está VALIDADA, el PDF sale como no verificado, con la marca "SIN VALIDAR" en cada hoja.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/evaluations/{evaluationId}/minuta")
@RequiredArgsConstructor
public class MinutaPdfController {

    private final MinutaPdfService minutaPdfService;
    private final EmailService emailService;
    private final MinutaService minutaService;
    private final EvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long evaluationId,
            @RequestParam(defaultValue = "false") boolean preview,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        byte[] bytes = minutaPdfService.generate(evaluationId, preview, validatedBy(evaluationId));
        Minuta minuta = minutaService.findByEvaluation(evaluationId).orElse(null);
        if (!preview) {
            Evaluation evaluation = require(evaluationId);
            minuta = minutaService.ensureForEvaluation(evaluation, user);
            if (minuta.getStatus() == MinutaStatus.BORRADOR) {
                minuta = minutaService.complete(minuta.getId());
            }
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"minuta-tecnica.pdf\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Minuta-Id, X-Minuta-Status");
        if (minuta != null) {
            response.header("X-Minuta-Id", String.valueOf(minuta.getId()))
                    .header("X-Minuta-Status", minuta.getStatus().name());
        }
        return response.body(bytes);
    }

    /** La minuta de esta evaluación (estado, quién la validó); 204 si todavía no hay. */
    @GetMapping("/info")
    public ResponseEntity<MinutaResponse> info(@PathVariable Long evaluationId) {
        return minutaService.findByEvaluation(evaluationId)
                .map(m -> ResponseEntity.ok(MinutaResponse.from(m)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/send")
    public Map<String, String> send(@PathVariable Long evaluationId, @Valid @RequestBody SendRequest request) {
        emailService.sendWithAttachment(
                request.to(), request.cc(), request.subject(), request.message(),
                minutaPdfService.generate(evaluationId, false, validatedBy(evaluationId)), "minuta-tecnica.pdf");
        return Map.of("message", "Minuta enviada correctamente");
    }

    /** Ediciones de la minuta (datos principales, observaciones, propuesta editada): llegan al PDF. */
    @GetMapping("/ediciones")
    public Map<String, Object> editions(@PathVariable Long evaluationId) {
        String json = require(evaluationId).getMinutaEdicionesJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    @PutMapping("/ediciones")
    public Map<String, Object> saveEditions(
            @PathVariable Long evaluationId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Evaluation evaluation = require(evaluationId);
        Minuta minuta = minutaService.findByEvaluation(evaluationId).orElse(null);
        if (minuta != null) {
            minutaService.assertCanEdit(minuta, user);
        }
        try {
            evaluation.setMinutaEdicionesJson(objectMapper.writeValueAsString(body));
        } catch (IOException e) {
            throw new IllegalArgumentException("Ediciones inválidas", e);
        }
        evaluationRepository.save(evaluation);
        // Nombre, contacto y teléfono se sincronizan con la lista de minutas.
        if (minuta != null) {
            minutaService.syncFromEditions(minuta, body);
        }
        return body;
    }

    private String validatedBy(Long evaluationId) {
        return minutaService.findByEvaluation(evaluationId)
                .filter(m -> m.getStatus() == MinutaStatus.VALIDADA)
                .map(m -> m.getValidatedByName() == null ? "Supervisor" : m.getValidatedByName())
                .orElse(null);
    }

    private Evaluation require(Long id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación " + id));
    }

    public record SendRequest(
            @NotBlank(message = "El destinatario es obligatorio")
            @Email(message = "El destinatario no es un correo válido")
            String to,
            String cc,
            @NotBlank(message = "El asunto es obligatorio") String subject,
            @NotBlank(message = "El mensaje es obligatorio") String message
    ) {
    }
}
