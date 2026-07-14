package com.upc.idbi.gateway.auth;

public record RegisterResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String company,
        String city,
        String role,
        String message
) {
}