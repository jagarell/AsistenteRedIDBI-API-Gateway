package com.upc.idbi.gateway.evidence.checklist;

import jakarta.persistence.*;
import lombok.*;

/** Una zona del local a fotografiar (checklist dinámico de evidencias).
 * Sembrada desde las respuestas del chat (ver EvidenceChecklistService) o
 * agregada a mano por el técnico durante la Fase A (isCustom=true). */
@Entity
@Table(name = "evidence_areas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;
}
