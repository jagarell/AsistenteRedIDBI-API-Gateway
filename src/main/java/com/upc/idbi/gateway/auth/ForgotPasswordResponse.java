package com.upc.idbi.gateway.auth;

public record ForgotPasswordResponse(
        String message,
        String recoveryCode,
        Integer expiresInMinutes
) {
}