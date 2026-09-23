package com.upc.idbi.gateway.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Integer expiresInMinutes,
        Long userId,
        String fullName,
        String email,
        String role
) {
}