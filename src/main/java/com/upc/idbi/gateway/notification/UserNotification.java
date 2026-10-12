package com.upc.idbi.gateway.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Aviso para un usuario (bandeja "Notificaciones" de la app). Se guarda siempre, aunque el usuario no tenga un
 * dispositivo registrado para push: así el aviso no se pierde si el push no llega.
 */
@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** MINUTA_COMPLETA, MINUTA_VALIDADA o MINUTA_POR_VENCER. */
    @Column(length = 40)
    private String type;

    @Column(name = "minuta_id")
    private Long minutaId;

    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
