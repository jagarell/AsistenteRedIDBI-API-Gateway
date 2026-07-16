package com.upc.idbi.gateway.auth;

/**
 * Respuesta de "olvidé mi contraseña". Por política de seguridad NO incluye el
 * código de recuperación: éste se entrega por un canal seguro (correo).
 */
public record ForgotPasswordResponse(
        String message,
        Integer expiresInMinutes
) {
}
