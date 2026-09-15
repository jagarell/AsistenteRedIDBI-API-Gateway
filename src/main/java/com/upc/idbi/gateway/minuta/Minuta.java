package com.upc.idbi.gateway.minuta;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Minuta técnica: documento del levantamiento de infraestructura de red de un
 * local, elaborado por un técnico y validado por un supervisor.
 *
 * <p>Visibilidad: por diseño todas las minutas (incluidas las que están en
 * borrador) son visibles para cualquier técnico o supervisor; el objetivo es
 * centralizar las minutas de todos los clientes y permitir que un técnico
 * continúe la minuta iniciada por otro.</p>
 */
@Entity
@Table(name = "minutas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Minuta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Evaluación de origen (opcional). */
    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(length = 255)
    private String address;

    /** Persona de contacto del cliente (para enviarle la propuesta). */
    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Notas adicionales libres del técnico (pantalla "Editar Propuesta"). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Técnico que creó la minuta. */
    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "technician_name", length = 150)
    private String technicianName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinutaStatus status;

    /** Diagnóstico/resumen técnico. */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Topología de red estructurada (JSON) construida a partir del chat. */
    @Column(name = "topology_json", columnDefinition = "TEXT")
    private String topologyJson;

    /** Contenido de la minuta (equipos, recomendaciones, etc.) en JSON. */
    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Supervisor que validó (nulo hasta que se valida). */
    @Column(name = "validated_by_id")
    private Long validatedById;

    @Column(name = "validated_by_name", length = 150)
    private String validatedByName;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
}
