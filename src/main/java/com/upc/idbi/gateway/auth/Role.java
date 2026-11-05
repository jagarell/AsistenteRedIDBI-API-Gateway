package com.upc.idbi.gateway.auth;

/**
 * Roles base del sistema (0.0.7). Lo que cada rol ve y hace se configura en
 * Equipo y accesos › Roles y permisos ({@code RolePermission}); estos son los roles base:
 * <ul>
 *     <li>{@link #TECNICO}: técnico de campo. Crea y edita minutas, y puede
 *     ver y continuar las minutas de cualquier técnico (incluso en borrador).</li>
 *     <li>{@link #SUPERVISOR}: líder TI. Además de lo anterior, es el único que
 *     puede validar una minuta completa.</li>
 *     <li>{@link #ADMINISTRADOR}: dueño de la cuenta de la empresa. Crea las cuentas, define roles, ve todas
 *     las minutas, configura las preguntas y reglas y revisa lo que aprendió el Asistente IA.</li>
 * </ul>
 * Base de datos existente: ver docs/0.0.7/migracion-0.0.7.sql (restricción del enum en users.role).
 */
public enum Role {
    TECNICO,
    SUPERVISOR,
    ADMINISTRADOR;

    /**
     * Convierte un texto libre (p. ej. proveniente del registro) a un rol válido.
     * Si el valor es nulo, vacío o desconocido, se asume {@link #TECNICO}.
     */
    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            return TECNICO;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TECNICO;
        }
    }

    /** Autoridad de Spring Security asociada (prefijo ROLE_). */
    public String authority() {
        return "ROLE_" + name();
    }
}
