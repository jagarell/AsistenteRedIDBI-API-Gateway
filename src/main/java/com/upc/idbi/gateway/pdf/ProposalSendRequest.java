package com.upc.idbi.gateway.pdf;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Envía la propuesta técnica (PDF generado en el momento) por correo. */
public record ProposalSendRequest(

        @NotBlank(message = "El destinatario es obligatorio")
        @Email(message = "El destinatario no es un correo válido")
        String to,

        String cc,

        @NotBlank(message = "El asunto es obligatorio")
        String subject,

        @NotBlank(message = "El mensaje es obligatorio")
        String message,

        @NotNull(message = "Los datos de la propuesta son obligatorios")
        @Valid
        ProposalPdfRequest proposal
) {
}
