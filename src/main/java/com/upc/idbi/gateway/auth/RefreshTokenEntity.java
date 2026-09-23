package com.upc.idbi.gateway.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Solo se guarda el hash SHA-256 del refresh token, nunca el valor en claro
 * (ver JwtService#hashToken) — si la base de datos se filtra, los refresh
 * tokens emitidos no sirven para nada sin el valor original.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 44)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
