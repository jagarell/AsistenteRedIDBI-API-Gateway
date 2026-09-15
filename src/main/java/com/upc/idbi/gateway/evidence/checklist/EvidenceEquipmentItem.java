package com.upc.idbi.gateway.evidence.checklist;

import jakarta.persistence.*;
import lombok.*;

/** Un equipo a fotografiar (checklist dinámico de evidencias). Sembrado
 * desde las cantidades reportadas en el chat, o agregado a mano por el
 * técnico en la Fase A (isCustom=true). `extractedSpecsJson` se llena solo
 * al analizar una foto con IA (marca/modelo detectados, ver
 * EvidenceChecklistService.uploadEquipmentPhoto). */
@Entity
@Table(name = "evidence_equipment_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceEquipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    /** router | switch | pos | printer | camera | computer | access_point | otro
     * — ver app.vision._EQUIPMENT_CATEGORIES en idbi-fastapi. */
    @Column(name = "equipment_type", nullable = false, length = 30)
    private String equipmentType;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;

    @Column(name = "extracted_specs_json", columnDefinition = "TEXT")
    private String extractedSpecsJson;

    @Column(name = "technician_notes", columnDefinition = "TEXT")
    private String technicianNotes;
}
