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

    /**
     * Motivo con el que el supervisor devolvió la minuta al técnico. La minuta vuelve a BORRADOR (no se agrega un
     * estado nuevo: la base tiene una restricción con los valores de MinutaStatus que `ddl-auto=update` no actualiza).
     * Se borra cuando el técnico la vuelve a completar.
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_by_name", length = 150)
    private String rejectedByName;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /** Cuándo se avisó al técnico que el borrador estaba por vencer (una sola vez). */
    @Column(name = "expiry_reminder_sent_at")
    private LocalDateTime expiryReminderSentAt;

    // ---- 0.0.7 ----

    /** IMPLEMENTACION (visita de evaluación) o SOPORTE (soporte / incidencia). Nulo = IMPLEMENTACION. */
    @Column(length = 20)
    private String tipo;

    /** Versión de la minuta; sube con cada actualización por el Asistente IA (historial en MinutaVersion). */
    @Column(name = "version_num")
    private Integer version;

    @Column(length = 80)
    private String rubro;

    /** Puntaje del análisis (0–100) para la criticidad en Reportes; nulo = sin análisis. */
    private Integer puntaje;

    /** Nivel de esfuerzo: Básica, Intermedia o Compleja (Plazos de la empresa). */
    @Column(length = 20)
    private String esfuerzo;

    /** Minutos de elaboración (desde la visita hasta enviarla), para Reportes. */
    @Column(name = "minutos_elaboracion")
    private Integer minutosElaboracion;

    /** Accesos y contraseñas que van en la minuta: [{eq, usr, pw}] en JSON. */
    @Column(name = "accesos_json", columnDefinition = "TEXT")
    private String accesosJson;

    /** Datos de la minuta de soporte (motivo, alcance, lo realizado, reglas S1–S4, estado final) en JSON. */
    @Column(name = "soporte_json", columnDefinition = "TEXT")
    private String soporteJson;

    /** Estado final de soporte: OP, OPP u OBS. */
    @Column(name = "estado_soporte", length = 5)
    private String estadoSoporte;

    public boolean isSoporte() {
        return "SOPORTE".equals(tipo);
    }

    public int versionOrOne() {
        return version == null ? 1 : version;
    }
}
