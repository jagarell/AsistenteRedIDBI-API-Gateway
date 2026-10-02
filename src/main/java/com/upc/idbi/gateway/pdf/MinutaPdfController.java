package com.upc.idbi.gateway.pdf;

import com.upc.idbi.gateway.email.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Minuta técnica de una evaluación en PDF (generada en el momento a partir de
 * las respuestas y evidencias del chat; no se almacena).
 */
@RestController
@RequestMapping("/api/evaluations/{evaluationId}/minuta")
@RequiredArgsConstructor
public class MinutaPdfController {

    private final MinutaPdfService minutaPdfService;
    private final EmailService emailService;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long evaluationId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"minuta-tecnica.pdf\"")
                .body(minutaPdfService.generate(evaluationId));
    }

    @PostMapping("/send")
    public Map<String, String> send(@PathVariable Long evaluationId, @Valid @RequestBody SendRequest request) {
        emailService.sendWithAttachment(
                request.to(), request.cc(), request.subject(), request.message(),
                minutaPdfService.generate(evaluationId), "minuta-tecnica.pdf");
        return Map.of("message", "Minuta enviada correctamente");
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
