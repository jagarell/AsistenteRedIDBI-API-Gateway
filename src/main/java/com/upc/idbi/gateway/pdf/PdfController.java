package com.upc.idbi.gateway.pdf;

import com.upc.idbi.gateway.email.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Genera el PDF de la propuesta técnica y lo devuelve como bytes crudos
 * (application/pdf), o lo envía por correo con el PDF adjunto. En ambos
 * casos el PDF se genera en el momento; no se almacena en el servidor.
 */
@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;
    private final EmailService emailService;

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@RequestBody ProposalPdfRequest request) {
        byte[] pdf = pdfService.generateProposalPdf(request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"propuesta.pdf\"")
                .body(pdf);
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> sendProposal(@Valid @RequestBody ProposalSendRequest request) {
        byte[] pdf = pdfService.generateProposalPdf(request.proposal());

        emailService.sendWithAttachment(
                request.to(),
                request.cc(),
                request.subject(),
                request.message(),
                pdf,
                "propuesta.pdf"
        );

        return Map.of("message", "Propuesta enviada correctamente");
    }
}
