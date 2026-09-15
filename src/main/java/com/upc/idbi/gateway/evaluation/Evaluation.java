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

    /** Checklist de evidencias (áreas/equipos a fotografiar): false = Fase A
     * (el técnico puede agregar/quitar ítems), true = Fase B (selección
     * congelada, cada ítem necesita al menos una foto). */
    @Builder.Default
    private Boolean evidenceSelectionLocked = false;
}