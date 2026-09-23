package com.upc.idbi.gateway.security;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Genera y valida JWT firmados (HS256). La clave de firma se lee de la
 * variable de entorno {@code JWT_SECRET}; nunca se hardcodea en el código.
 */
@Service
public class JwtService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey signingKey;
    private final long expirationMinutes;
    private final long refreshExpirationDays;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes,
            @Value("${app.jwt.refresh-expiration-days:30}") long refreshExpirationDays
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos 32 bytes para HS256. "
                            + "Configura la variable de entorno JWT_SECRET con un valor seguro."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMinutes = expirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    /** Minutos de validez del token (informativo para el cliente). */
    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    /** Días de validez del refresh token. */
    public long getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    /** Refresh token opaco (256 bits), sin relación con el formato JWT — se
     *  guarda hasheado (ver {@link #hashToken}) y se rota en cada uso. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 del refresh token, para guardarlo en la base de datos sin
     *  exponer nunca el valor en claro (ver RefreshTokenEntity). */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    public String generateToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida la firma y expiración del token y devuelve el usuario autenticado.
     * Lanza una excepción de JJWT si el token es inválido o ha expirado.
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long id = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);
        Role role = Role.fromString(claims.get("role", String.class));

        return new AuthenticatedUser(id, email, name, role);
    }
}
