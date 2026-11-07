package com.upc.idbi.gateway.bitacora;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Trazabilidad (doc 0.0.7 §1, §24). El administrador ve toda la empresa; técnico y supervisor, solo una minuta propia o de su equipo. */
@RestController
@RequiredArgsConstructor
public class BitacoraController {

    private final BitacoraRepository repo;
    private final MinutaRepository minutas;
    private final com.upc.idbi.gateway.auth.UserRepository users;

    private static Map<String, Object> fila(Bitacora b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("minutaId", b.getMinutaId());
        m.put("usuario", b.getUsuarioNombre());
        m.put("rol", b.getRol());
        m.put("tipo", b.getTipo());
        m.put("accion", b.getAccion());
        m.put("ts", b.getTs().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return m;
    }

    @GetMapping("/api/bitacora")
    public List<Map<String, Object>> bitacora(@RequestParam(required = false) Long minutaId, @AuthenticationPrincipal AuthenticatedUser user) {
        if (minutaId != null) {
            Minuta m = minutas.findById(minutaId).orElse(null);
            if (m == null || !puedeVer(m, user)) {
                return List.of();
            }
            return repo.findByMinutaIdOrderByTsDesc(minutaId).stream().map(BitacoraController::fila).toList();
        }
        if (!user.isAdmin()) {
            return repo.findByUsuarioIdOrderByTsDesc(user.id()).stream().limit(100).map(BitacoraController::fila).toList();
        }
        return repo.findTop200ByOrderByTsDesc().stream().map(BitacoraController::fila).toList();
    }

    /** Resumen de una minuta: quién la creó, la rechazó (con motivo), la validó y quién la editó en las últimas 48 h. */
    @GetMapping("/api/minutas/{id}/bitacora")
    public Map<String, Object> resumen(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        Minuta m = minutas.findById(id).orElse(null);
        if (m == null || !puedeVer(m, user)) {
            return Map.of("eventos", List.of());
        }
        List<Bitacora> ev = repo.findByMinutaIdOrderByTsDesc(id);
        LocalDateTime corte = LocalDateTime.now().minusHours(48);
        Map<String, Long> editores = new LinkedHashMap<>();
        ev.stream().filter(b -> "editar".equals(b.getTipo()) && b.getTs().isAfter(corte)).forEach(b -> editores.merge(b.getUsuarioNombre(), 1L, Long::sum));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("creadaPor", m.getTechnicianName());
        r.put("rechazadaPor", m.getRejectedByName());
        r.put("motivo", m.getRejectionReason());
        r.put("validadaPor", m.getValidatedByName());
        r.put("editores48h", editores);
        r.put("eventos", ev.stream().map(BitacoraController::fila).toList());
        return r;
    }

    private boolean puedeVer(Minuta m, AuthenticatedUser user) {
        if (user.isAdmin() || Objects.equals(m.getTechnicianId(), user.id())) {
            return true;
        }
        return user.isSupervisor() && users.findById(m.getTechnicianId() == null ? -1L : m.getTechnicianId())
                .map(t -> Objects.equals(t.getSupervisorId(), user.id())).orElse(false);
    }
}
