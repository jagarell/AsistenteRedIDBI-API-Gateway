package com.upc.idbi.gateway.acceso;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Acceso temporal a la minuta de otro técnico o de otro equipo (doc 0.0.7 §46): el técnico lo pide a su supervisor y el supervisor al
 * administrador, con un motivo. El código es de 6 dígitos, vale 24 horas, abre solo esa minuta y solo para quien la pidió.
 */
@Entity
@Table(name = "solicitudes_acceso")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minuta_id", nullable = false)
    private Long minutaId;

    @Column(name = "minuta_local", length = 150)
    private String minutaLocal;

    @Column(name = "solicitante_id", nullable = false)
    private Long solicitanteId;

    @Column(name = "solicitante_nombre", length = 150)
    private String solicitanteNombre;

    @Column(length = 200)
    private String motivo;

    /** pendiente | aprobada | rechazada | anulada. */
    @Column(nullable = false, length = 12)
    private String estado;

    @Column(length = 6)
    private String codigo;

    /** Quién aprobó. */
    @Column(name = "aprobador_nombre", length = 150)
    private String aprobadorNombre;

    /** El solicitante escribió el código y abrió la minuta. */
    @Column(name = "canjeada_en")
    private LocalDateTime canjeadaEn;

    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;
}
