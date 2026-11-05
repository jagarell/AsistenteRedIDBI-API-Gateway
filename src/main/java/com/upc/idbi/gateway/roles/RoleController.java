package com.upc.idbi.gateway.roles;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Equipo y accesos › Roles y permisos (web y app del administrador). */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<RolePermission> list() {
        return service.list();
    }

    /** Pestañas de Reportes del usuario que pregunta. */
    @GetMapping("/mi-perfil-reportes")
    public RoleService.ReportProfile myProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return RoleService.profile(service.forRole(user.role()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public RolePermission update(@PathVariable String id, @RequestBody RoleRequest req) {
        return service.update(id, req.name(), req.perms() == null ? Set.of() : req.perms());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public RolePermission create(@RequestBody RoleRequest req) {
        return service.create(req.name(), req.perms() == null ? Set.of() : req.perms());
    }

    public record RoleRequest(String name, Set<String> perms) {
    }
}
