package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.List;

/** Vista consolidada de solo lectura de una evaluación: respuestas del chat
 * + checklist de evidencias + tabla de equipos. Sirve la pantalla principal
 * de propuesta (MinutaProposalFragment vía MinutaViewModel.load()). */
public record MinutaDto(
        Long evaluationId,
        String establishmentName,
        String establishmentAddress,
        String establishmentType,
        List<ChatAnswerDto> conversationResponses,
        List<EvidenceAreaItemDto> areas,
        List<EvidenceEquipmentItemDto> equipment,
        List<EquipmentTableRowDto> equipmentTable
) {
}
