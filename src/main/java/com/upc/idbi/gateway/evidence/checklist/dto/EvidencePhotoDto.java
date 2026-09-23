package com.upc.idbi.gateway.evidence.checklist.dto;

import java.time.LocalDateTime;

public record EvidencePhotoDto(
        Long id,
        String fileUrl,
        String comment,
        LocalDateTime capturedAt,
        /** Descripción libre que devolvió el análisis de IA (marca/modelo van
         *  aparte, en EvidenceEquipmentItemDto.extractedSpecs) — antes se
         *  calculaba pero nunca se exponía en la API, así que nunca llegaba
         *  al técnico. Null para fotos de área (no pasan por análisis). */
        String analysisResult
) {
}
