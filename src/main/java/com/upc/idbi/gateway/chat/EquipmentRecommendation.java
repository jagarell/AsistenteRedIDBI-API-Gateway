package com.upc.idbi.gateway.chat;

public record EquipmentRecommendation(
        String name,
        String description,
        Integer quantity,
        // Pendiente de catálogo real de precios de IDBI — null hasta que
        // exista un catálogo real (mismo patrón que OPENAI_API_KEY/RUC).
        Double unitPrice
) {
}