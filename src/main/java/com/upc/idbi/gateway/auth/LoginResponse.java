package com.upc.idbi.gateway.auth;

public record LoginResponse(
        String accessToken,
        Integer expiresInMinutes,
        Long userId,
        String fullName,
        String email,
        String role
) {
}