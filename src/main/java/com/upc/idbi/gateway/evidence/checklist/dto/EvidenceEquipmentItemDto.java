package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.List;
import java.util.Map;

public record EvidenceEquipmentItemDto(
        Long id,
        String equipmentType,
        String label,
        boolean isCustom,
        Map<String, Object> extractedSpecs,
        String technicianNotes,
        List<EvidencePhotoDto> photos
) {
}
