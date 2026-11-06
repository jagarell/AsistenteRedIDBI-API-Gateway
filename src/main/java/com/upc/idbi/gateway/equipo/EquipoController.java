package com.upc.idbi.gateway.equipo;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Equipo y accesos (administrador): ver al equipo, cambiar el rol y asignar supervisor a un técnico. */
@RestController
@RequestMapping("/api/equipo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class EquipoController {

    private final UserRepository users;

    @GetMapping
    public List<Map<String, Object>> lista() {
        List<UserEntity> todos = users.findAll();
        Map<Long, UserEntity> porId = new HashMap<>();
        todos.forEach(u -> porId.put(u.getId(), u));
        return todos.stream()
                .sorted(Comparator.comparing(UserEntity::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("nombre", u.getFullName());
                    m.put("email", u.getEmail());
                    m.put("rol", u.getRole().name());
                    m.put("supervisorId", u.getSupervisorId());
                    UserEntity s = u.getSupervisorId() == null ? null : porId.get(u.getSupervisorId());
                    m.put("supervisorNombre", s == null ? null : s.getFullName());
                    m.put("tecnicosACargo", todos.stream().filter(t -> u.getId().equals(t.getSupervisorId())).count());
                    m.put("pidioCodigo", u.getResetCode() != null && u.getResetCodeExpiresAt() != null
                            && u.getResetCodeExpiresAt().isAfter(java.time.LocalDateTime.now()));
                    return m;
                }).toList();
    }

    @PutMapping("/{id}/rol")
    public Map<String, Object> rol(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthenticatedUser me) {
        UserEntity u = find(id);
        Role nuevo;
        try {
            nuevo = Role.valueOf(String.valueOf(body.get("rol")).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no válido.");
        }
        if (u.getId().equals(me.id()) && nuevo != Role.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes quitarte tu propio rol de administrador.");
        }
        if (u.getRole() == Role.SUPERVISOR && nuevo != Role.SUPERVISOR) {
            // Si un supervisor deja de serlo, sus técnicos quedan sin supervisor asignado.
            users.findAll().stream().filter(t -> u.getId().equals(t.getSupervisorId())).forEach(t -> {
                t.setSupervisorId(null);
                users.save(t);
            });
        }
        u.setRole(nuevo);
        if (nuevo != Role.TECNICO) u.setSupervisorId(null);
        users.save(u);
        return Map.of("id", u.getId(), "rol", u.getRole().name());
    }

    @PutMapping("/{id}/supervisor")
    public Map<String, Object> supervisor(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserEntity u = find(id);
        if (u.getRole() != Role.TECNICO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo un técnico tiene supervisor.");
        }
        Object raw = body.get("supervisorId");
        Long sid = raw == null ? null : Long.valueOf(raw.toString());
        if (sid != null && find(sid).getRole() != Role.SUPERVISOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La persona elegida no es supervisor.");
        }
        u.setSupervisorId(sid);
        users.save(u);
        return Map.of("id", u.getId(), "supervisorId", sid == null ? "" : sid);
    }

    private UserEntity find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa persona."));
    }
}
