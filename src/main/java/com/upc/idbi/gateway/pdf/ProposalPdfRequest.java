package com.upc.idbi.gateway.pdf;

import java.util.List;

/**
 * Datos de la propuesta técnica a partir de los cuales se genera el PDF.
 * El cliente (app Android) ya tiene esta información en pantalla (viene del
 * chat de 20 nodos + AnalysisEngine); el backend solo la compone en un PDF.
 */
public record ProposalPdfRequest(
        String establishmentName,
        String address,
        String technicianName,
        Integer score,
        String summary,
        List<String> asIsFindings,
        List<String> recommendations,
        List<EquipmentLineDto> equipment,
        String topologyText
) {

    public record EquipmentLineDto(
            String name,
            String description,
            int quantity,
            // Pendiente de catálogo real de precios de IDBI — null hasta
            // entonces (mismo patrón que OPENAI_API_KEY/RUC).
            Double unitPrice
    ) {
    }
}
