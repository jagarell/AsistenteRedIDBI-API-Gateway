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

    /**
     * Bitácora de la minuta (doc 0.0.7 §24): resumen (creada por, con qué, estado, rechazada por y quién la corrigió, validada por),
     * quiénes la editaron en las últimas 48 horas e historial con el detalle en palabras simples.
     */
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
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        Bitacora crear = ev.stream().filter(b -> "crear".equals(b.getTipo())).reduce((x, y) -> y).orElse(null);
        String herramienta = crear != null && crear.getAccion() != null && crear.getAccion().contains("Asistente IA") ? "Asistente IA" : "Chat IA";
        // Quién la corrigió después de un rechazo: el primer «Solicitó la validación» posterior al rechazo.
        String corregidaPor = null;
        String corregidaEn = null;
        if (m.getRejectedAt() != null) {
            for (int i = ev.size() - 1; i >= 0; i--) {
                Bitacora b = ev.get(i);
                if ("solicitar".equals(b.getTipo()) && b.getTs().isAfter(m.getRejectedAt())) {
                    corregidaPor = b.getUsuarioNombre();
                    corregidaEn = b.getTs().format(f);
                    break;
                }
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("creadaPor", m.getTechnicianName());
        r.put("creadaEn", m.getCreatedAt() == null ? null : m.getCreatedAt().format(f));
        r.put("herramienta", herramienta);
        r.put("estado", com.upc.idbi.gateway.reportes.ReportesService.estadoDe(m));
        r.put("version", m.versionOrOne());
        r.put("rechazadaPor", m.getRejectedByName());
        r.put("rechazadaEn", m.getRejectedAt() == null ? null : m.getRejectedAt().format(f));
        r.put("motivo", m.getRejectionReason());
        r.put("corregidaPor", corregidaPor);
        r.put("corregidaEn", corregidaEn);
        r.put("validadaPor", m.getValidatedByName());
        r.put("validadaEn", m.getValidatedAt() == null ? null : m.getValidatedAt().format(f));
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
