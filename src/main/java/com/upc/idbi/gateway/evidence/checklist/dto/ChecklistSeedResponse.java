package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.List;

/** Respuesta de FastAPI POST /evidence-checklist/seed — ver
 * app.chat.checklist.build_evidence_checklist en idbi-fastapi. */
public record ChecklistSeedResponse(
        List<AreaSeed> areas,
        List<EquipmentSeed> equipment
) {
    public record AreaSeed(String name) {
    }

    public record EquipmentSeed(String equipmentType, String label) {
    }
}
