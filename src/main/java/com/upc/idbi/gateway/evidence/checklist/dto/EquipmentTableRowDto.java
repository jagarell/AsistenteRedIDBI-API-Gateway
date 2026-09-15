package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.Map;

public record EquipmentTableRowDto(
        String label,
        String equipmentType,
        Map<String, Object> extractedSpecs,
        String technicianNotes,
        int photoCount
) {
}
