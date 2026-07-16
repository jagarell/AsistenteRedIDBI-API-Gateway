package com.upc.idbi.gateway.auth;

/**
 * Entrega del código de recuperación por un canal seguro. La implementación
 * real debe usar el servicio de correo corporativo. El código nunca debe
 * exponerse en respuestas HTTP ni registrarse en logs en texto plano.
 */
public interface RecoveryCodeNotifier {
    void send(String email, String recoveryCode, int expiresInMinutes);
}
