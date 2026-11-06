package com.upc.idbi.gateway.roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rol de Equipo y accesos › Roles y permisos: los base (admin, sup, tec) y los que crea la empresa.
 * Permisos de Reportes: m_all (General), r_tec_all (Por técnico) o r_tec_mine (Mis técnicos), a_create (Mis minutas).
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    /** admin, sup, tec o customN. */
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    /** fijo, base o propio. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(length = 400)
    private String description;

    /** Permisos separados por coma. */
    @Column(columnDefinition = "TEXT")
    private String perms;

    public Set<String> permSet() {
        if (perms == null || perms.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(perms.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setPermSet(Set<String> set) {
        this.perms = String.join(",", set);
    }
}
