package com.upc.idbi.gateway.evidence;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Foto de evidencia técnica (Router, Switch, POS, plano del local,
 * topología...) tomada durante una evaluación. El archivo se guarda en disco
 * local (ver {@link EvidenceStorageService}); esta fila solo registra la
 * metadata y, si se ejecutó, el resultado del análisis de visión IA.
 */
@Entity
@Table(name = "evidence_photos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    /** router | switch | pos | camera | kitchen | hall | servers | plan | topology */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    /** Marca/modelo detectados por visión IA en la foto (solo se intenta para
     * categorías de equipo — ver app.vision._EQUIPMENT_CATEGORIES). Null si
     * no se pudo leer con claridad; nunca se inventa. */
    @Column(name = "detected_brand", length = 100)
    private String detectedBrand;

    @Column(name = "detected_model", length = 100)
    private String detectedModel;

    /** Comentario del técnico al momento de capturar la foto (checklist
     * dinámico — ver evidence.checklist). Null en el flujo simple viejo. */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /** A qué ítem del checklist dinámico pertenece esta foto — exactamente
     * uno de los dos, o ninguno (flujo simple viejo de 7 categorías fijas). */
    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "equipment_item_id")
    private Long equipmentItemId;
}
