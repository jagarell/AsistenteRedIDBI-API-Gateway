package com.upc.idbi.gateway.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Solicitud de cambio de contraseña con aprobación del administrador (doc 0.0.7 §36, estilo Google): el administrador ve un número de
 * 2 dígitos y el técnico elige ese número entre 3. Vence a los 10 minutos.
 */
@Entity
@Table(name = "solicitudes_recuperacion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 150)
    private String nombre;

    @Column(length = 20)
    private String rol;

    @Column(nullable = false, length = 2)
    private String numero;

    /** esperando | aprobada | rechazada. */
    @Column(nullable = false, length = 12)
    private String estado;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;
}
