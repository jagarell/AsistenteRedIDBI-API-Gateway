package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.List;

public record EvidenceChecklistDto(
        Long evaluationId,
        boolean selectionLocked,
        List<EvidenceAreaItemDto> areas,
        List<EvidenceEquipmentItemDto> equipment,
        boolean allItemsHavePhoto
) {
}
