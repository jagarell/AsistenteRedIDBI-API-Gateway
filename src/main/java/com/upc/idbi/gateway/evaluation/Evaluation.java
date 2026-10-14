package com.upc.idbi.gateway.evaluation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String restaurantName;
    private String location;
    private String address;

    /** Tipo de establecimiento (Restaurante/Cafetería/Bar/Comida rápida/Otro),
     * respondido en el chat técnico — se sincroniza al completar el chat, ver
     * ChatService.persistAnswers. */
    private String establishmentType;
    private String contactName;
    private String contactEmail;
    private String phone;

    @Enumerated(EnumType.STRING)
    private EvaluationStatus status;

    private Integer progress;
    private Integer score;

    private LocalDateTime createdAt;

    /** Respuestas crudas del chat técnico (23 nodos), serializadas a JSON, una
     * vez que el chat se completa. Permite que /analysis y el sembrado del
     * checklist de evidencias funcionen a partir del evaluationId solo, sin
     * que el cliente tenga que reenviar las respuestas. */
    @Column(columnDefinition = "TEXT")
    private String chatAnswersJson;

    /** Mapa de red editable (JSON: nodos, enlaces, textos, imágenes) — ver MapController. */
    @Column(columnDefinition = "TEXT")
    private String mapJson;

    /** Checklist de evidencias (áreas/equipos a fotografiar): false = Fase A
     * (el técnico puede agregar/quitar ítems), true = Fase B (selección
     * congelada, cada ítem necesita al menos una foto). */
    @Builder.Default
    private Boolean evidenceSelectionLocked = false;

    /**
     * Anulada a mano por el técnico (solo desde BORRADOR). Es una marca aparte del estado a
     * propósito: la base ya tiene una restricción con los valores de EvaluationStatus y
     * `ddl-auto=update` no la actualiza, así que un estado nuevo se rechazaría. Null = no anulada
     * (filas anteriores a esta columna). No aparece en listas ni en los conteos.
     */
    private Boolean annulled;

    /**
     * Ediciones de la minuta (JSON): datos principales, observaciones, propuesta editada y cambios pedidos en el chat.
     * Se aplican al armar el documento del PDF; no se pierden si el análisis se recalcula.
     */
    @Column(columnDefinition = "TEXT")
    private String minutaEdicionesJson;

    /**
     * Estado que ve el Historial (no se guarda): EN_CURSO = el chat no terminó; BORRADOR/COMPLETA/VALIDADA =
     * el de su minuta, o BORRADOR si el chat terminó y todavía no hay minuta. Lo rellena EvaluationEstadoService.
     */
    @Transient
    private String minutaStatus;
    @Transient
    private Long minutaId;
    @Transient
    private String validatedByName;
    @Transient
    private String technicianName;
    /** Motivo del rechazo mientras la minuta está devuelta al técnico (null si no lo está). */
    @Transient
    private String rejectionReason;
}
