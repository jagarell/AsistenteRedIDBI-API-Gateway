package com.upc.idbi.gateway.minuta.dto;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaStatus;

import java.time.LocalDateTime;

/**
 * Vista de una minuta expuesta por la API.
 */
public record MinutaResponse(
        Long id,
        Long evaluationId,
        String clientName,
        String address,
        Long technicianId,
        String technicianName,
        MinutaStatus status,
        String summary,
        String topologyJson,
        String contentJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long validatedById,
        String validatedByName,
        LocalDateTime validatedAt
) {
    public static MinutaResponse from(Minuta m) {
        return new MinutaResponse(
                m.getId(),
                m.getEvaluationId(),
                m.getClientName(),
                m.getAddress(),
                m.getTechnicianId(),
                m.getTechnicianName(),
                m.getStatus(),
                m.getSummary(),
                m.getTopologyJson(),
                m.getContentJson(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getValidatedById(),
                m.getValidatedByName(),
                m.getValidatedAt()
        );
    }
}
