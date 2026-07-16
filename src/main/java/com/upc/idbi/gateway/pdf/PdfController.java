package com.upc.idbi.gateway.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Genera el PDF de la propuesta técnica y lo devuelve como bytes crudos
 * (application/pdf). El cliente (app) descarga el cuerpo de la respuesta y lo
 * guarda/abre localmente; no hay almacenamiento del PDF en el servidor.
 */
@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@RequestBody ProposalPdfRequest request) {
        byte[] pdf = pdfService.generateProposalPdf(request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"propuesta.pdf\"")
                .body(pdf);
    }
}
