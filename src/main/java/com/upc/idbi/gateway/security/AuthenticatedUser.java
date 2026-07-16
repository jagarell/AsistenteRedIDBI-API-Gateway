package com.upc.idbi.gateway.security;

import com.upc.idbi.gateway.auth.Role;

/**
 * Usuario autenticado extraído del JWT. Se expone como principal de Spring
 * Security para que los controladores puedan resolver quién realiza la acción
 * (por ejemplo, el técnico que crea una minuta) sin volver a consultar la BD.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String fullName,
        Role role
) {
    public boolean isSupervisor() {
        return role == Role.SUPERVISOR;
    }
}
