package com.upc.idbi.gateway.auth;

/**
 * Roles del sistema. Por diseño (ver tesis IDBI, HU12/HU15) solo se manejan
 * dos roles operativos:
 * <ul>
 *     <li>{@link #TECNICO}: técnico de campo. Crea y edita minutas, y puede
 *     ver y continuar las minutas de cualquier técnico (incluso en borrador).</li>
 *     <li>{@link #SUPERVISOR}: líder TI. Además de lo anterior, es el único que
 *     puede validar una minuta completa.</li>
 * </ul>
 * No existe rol administrador; el control de acceso se aplica por código.
 */
public enum Role {
    TECNICO,
    SUPERVISOR;

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
