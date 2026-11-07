package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bandeja de notificaciones del usuario: lista, no leídas y marcar como leídas. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class UserNotificationController {

    private final UserNotificationRepository repository;
    private final com.upc.idbi.gateway.minuta.MinutaRepository minutas;
    private final com.upc.idbi.gateway.auth.UserRepository users;
    private final PushNotificationService push;
    private final com.upc.idbi.gateway.empresa.EmpresaService empresa;
    private final com.upc.idbi.gateway.bitacora.BitacoraService bitacora;
    private final com.upc.idbi.gateway.evaluation.EvaluationRepository evaluations;
    private final AvisosService avisosService;

    @GetMapping
    public Map<String, Object> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Map<String, Object>> items = repository.findTop50ByUserIdOrderByCreatedAtDesc(user.id()).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("type", n.getType());
            m.put("minutaId", n.getMinutaId());
            m.put("evaluationId", n.getEvaluationId());
            m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
            m.put("read", n.getReadAt() != null);
            return m;
        }).toList();
        return Map.of("items", items, "unread", repository.countByUserIdAndReadAtIsNull(user.id()));
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        repository.markAllRead(user.id(), LocalDateTime.now());
        return Map.of("unread", 0);
    }

    /**
     * Bandeja por rol (doc 0.0.7 §44). Supervisor: lo que le enviaron sus técnicos; administrador: todas las minutas de la empresa;
     * técnico: sus minutas («Tu minuta de …»). Filtros con cantidad y «Otros avisos» (recordatorios, por vencer, códigos…).
     */
    @GetMapping("/bandeja")
    public Map<String, Object> bandeja(@AuthenticationPrincipal AuthenticatedUser user) {
        var todas = minutas.findAllByOrderByCreatedAtDesc().stream().filter(empresa::visible).toList();
        java.util.List<String> nombresEquipo = new java.util.ArrayList<>();
        java.util.List<com.upc.idbi.gateway.minuta.Minuta> alcance;
        if (user.isAdmin()) {
            alcance = todas;
        } else if (user.isSupervisor()) {
            var equipo = users.findByRole(com.upc.idbi.gateway.auth.Role.TECNICO).stream()
                    .filter(t -> java.util.Objects.equals(t.getSupervisorId(), user.id())).toList();
            equipo.forEach(t -> nombresEquipo.add(t.getFullName()));
            var ids = equipo.stream().map(com.upc.idbi.gateway.auth.UserEntity::getId).collect(java.util.stream.Collectors.toSet());
            alcance = todas.stream().filter(m -> m.getTechnicianId() != null && ids.contains(m.getTechnicianId())).toList();
        } else {
            alcance = todas.stream().filter(m -> java.util.Objects.equals(m.getTechnicianId(), user.id())).toList();
        }
        var fmtF = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var fmtH = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.util.List<Map<String, Object>> lista = new java.util.ArrayList<>();
        for (var m : alcance) {
            String estado = com.upc.idbi.gateway.reportes.ReportesService.estadoDe(m);
            if (!java.util.Set.of("Completa", "En revisión", "Validada").contains(estado)) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("evaluationId", m.getEvaluationId());
            r.put("tecnico", m.getTechnicianName());
            r.put("local", m.getClientName());
            r.put("puntaje", m.getPuntaje() == null ? 0 : m.getPuntaje());
            r.put("estado", estado);
            r.put("fecha", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(fmtF));
            r.put("hora", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(fmtH));
            r.put("topologia", m.getTopologyJson() != null && !m.getTopologyJson().isBlank());
            lista.add(r);
        }
        Map<String, Object> cnt = new LinkedHashMap<>();
        cnt.put("ALL", lista.size());
        for (String e : new String[]{"Completa", "En revisión", "Validada"}) {
            cnt.put(e, lista.stream().filter(x -> e.equals(x.get("estado"))).count());
        }
        var tipos = user.role() == com.upc.idbi.gateway.auth.Role.TECNICO
                ? java.util.Set.of("MINUTA_VALIDADA", "MINUTA_RECHAZADA") : java.util.Set.of("MINUTA_COMPLETA");
        var otros = repository.findTop50ByUserIdOrderByCreatedAtDesc(user.id()).stream()
                .filter(n -> n.getType() == null || !tipos.contains(n.getType()))
                .map(n -> {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("id", n.getId());
                    o.put("titulo", n.getTitle());
                    o.put("texto", n.getBody());
                    o.put("tipo", n.getType());
                    o.put("minutaId", n.getMinutaId());
                    o.put("evaluationId", n.getEvaluationId());
                    o.put("refId", n.getRefId());
                    o.put("leida", n.getReadAt() != null);
                    o.put("fecha", n.getCreatedAt() == null ? "" : n.getCreatedAt().toString());
                    return o;
                }).toList();
        String desc = user.isAdmin() ? "Ves las minutas de todos los usuarios de la empresa."
                : user.isSupervisor() ? (nombresEquipo.isEmpty() ? "Solo ves lo que te enviaron tus técnicos. Aún no tienes técnicos a cargo."
                : "Solo ves lo que te enviaron tus técnicos: " + String.join(", ", nombresEquipo) + ".")
                : "Aquí ves el estado de tus minutas y tus avisos.";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rol", user.role().name());
        out.put("descripcion", desc);
        out.put("conteos", cnt);
        out.put("minutas", lista);
        out.put("otros", otros);
        return out;
    }

    /** «Avisarme cuando…»: las opciones según el rol y lo que cada usuario marcó. */
    @org.springframework.web.bind.annotation.GetMapping("/avisos")
    public Map<String, Object> avisos(@AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Boolean> estado = avisosService.estado(user.id(), user.role());
        List<Map<String, Object>> l = new java.util.ArrayList<>();
        AvisosService.opciones(user.role()).forEach((k, t) -> l.add(Map.of("clave", k, "texto", t, "activo", estado.getOrDefault(k, true))));
        return Map.of("opciones", l);
    }

    @org.springframework.web.bind.annotation.PutMapping("/avisos")
    public Map<String, Boolean> guardarAvisos(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> cambios, @AuthenticationPrincipal AuthenticatedUser user) {
        return avisosService.guardar(user.id(), user.role(), cambios);
    }

    /** «Pedir que la termine» para una evaluación que todavía no es minuta (Borrador · sin minuta). */
    private Map<String, Object> recordatorioEvaluacion(Long evaluationId, AuthenticatedUser user) {
        var e = evaluations.findById(evaluationId).orElseThrow(() -> new IllegalArgumentException("Evaluación no encontrada"));
        var destino = e.getCreadoPorId() == null ? null : users.findById(e.getCreadoPorId()).orElse(null);
        if (destino == null) {
            throw new IllegalArgumentException("No hay a quién avisar");
        }
        String nombre = e.getRestaurantName() == null || e.getRestaurantName().isBlank() ? "tu evaluación" : e.getRestaurantName();
        String texto = (user.isAdmin() ? "Administrador " : "Supervisor ") + user.fullName() + " te pide: termina la minuta de " + nombre + ".";
        bitacora.registrar(user, null, "aviso", texto);
        repository.save(UserNotification.builder().userId(destino.getId()).title("Recordatorio").body(texto).type("RECORDATORIO")
                .evaluationId(e.getId()).createdAt(LocalDateTime.now()).build());
        push.trySend(destino.getFcmToken(), "Recordatorio", texto, Map.of("type", "RECORDATORIO", "evaluationId", String.valueOf(e.getId())));
        return Map.of("ok", true, "aviso", destino.getFullName());
    }

    /**
     * Recordatorio al técnico (doc 0.0.7 §29). Supervisor y administrador piden: terminar (borrador), solicitar la validación (pendiente)
     * o corregir (en revisión). Solo el administrador puede además recordar al supervisor.
     */
    @PostMapping("/recordatorio")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERVISOR')")
    @Transactional
    public Map<String, Object> recordatorio(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> req,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        if (req.get("minutaId") == null && req.get("evaluationId") != null) {
            return recordatorioEvaluacion(((Number) req.get("evaluationId")).longValue(), user);
        }
        Long minutaId = ((Number) req.get("minutaId")).longValue();
        String accion = String.valueOf(req.getOrDefault("accion", "terminar"));
        var m = minutas.findById(minutaId).orElseThrow(() -> new IllegalArgumentException("Minuta no encontrada"));
        com.upc.idbi.gateway.auth.UserEntity destino;
        String texto;
        if ("supervisor".equals(accion)) {
            if (!user.isAdmin()) {
                throw new org.springframework.security.access.AccessDeniedException("Solo el administrador recuerda al supervisor");
            }
            var tec = users.findById(m.getTechnicianId()).orElse(null);
            destino = tec == null || tec.getSupervisorId() == null ? null : users.findById(tec.getSupervisorId()).orElse(null);
            texto = user.fullName() + " te recuerda validar la minuta de " + m.getClientName() + ".";
        } else {
            destino = users.findById(m.getTechnicianId()).orElse(null);
            String pedido = switch (accion) {
                case "solicitar" -> "solicita la validación de la minuta de ";
                case "corregir" -> "corrige la minuta de ";
                default -> "termina la minuta de ";
            };
            texto = (user.isAdmin() ? "Administrador " : "Supervisor ") + user.fullName() + " te pide: " + pedido + m.getClientName() + ".";
        }
        if (destino == null) {
            throw new IllegalArgumentException("No hay a quién avisar");
        }
        bitacora.registrar(user, m.getId(), "aviso", texto);
        repository.save(UserNotification.builder().userId(destino.getId()).title("Recordatorio").body(texto).type("RECORDATORIO")
                .minutaId(m.getId()).evaluationId(m.getEvaluationId()).createdAt(LocalDateTime.now()).build());
        push.trySend(destino.getFcmToken(), "Recordatorio", texto, Map.of("type", "RECORDATORIO", "minutaId", String.valueOf(m.getId())));
        return Map.of("ok", true, "aviso", destino.getFullName());
    }
}
