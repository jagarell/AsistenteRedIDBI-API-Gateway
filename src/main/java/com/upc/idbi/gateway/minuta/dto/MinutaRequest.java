package com.upc.idbi.gateway.minuta.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos para crear o actualizar una minuta. El técnico/supervisor se toma del
 * usuario autenticado, no del cuerpo de la petición.
 */
public record MinutaRequest(

        Long evaluationId,

        @NotBlank(message = "El nombre del cliente es obligatorio")
        String clientName,

        String address,

        String contactName,

        String contactPhone,

        String notes,

        String summary,

        String topologyJson,

        String contentJson
) {
}
