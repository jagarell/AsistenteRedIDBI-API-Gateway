package com.upc.idbi.gateway.acceso;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.bitacora.BitacoraService;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.notification.UserNotification;
import com.upc.idbi.gateway.notification.UserNotificationRepository;
import com.upc.idbi.gateway.reportes.ReportesService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Rutas del acceso temporal a minutas de otros (doc 0.0.7 §46). El administrador ya ve todo; no necesita pedir acceso. */
@RestController
@RequestMapping("/api/accesos")
@RequiredArgsConstructor
public class AccesoController {

    private static final SecureRandom RND = new SecureRandom();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SolicitudAccesoRepository repo;
    private final MinutaRepository minutas;
    private final UserRepository users;
    private final UserNotificationRepository notificaciones;
    private final BitacoraService bitacora;

    /** Minutas que el usuario puede pedir: el técnico, las de otros técnicos; el supervisor, las de técnicos que no son de su equipo. */
    @GetMapping("/candidatas")
    public List<Map<String, Object>> candidatas(@AuthenticationPrincipal AuthenticatedUser me) {
        if (me.isAdmin()) {
            return List.of();
        }
        Set<Long> equipo = new HashSet<>();
        if (me.isSupervisor()) {
            users.findByRole(Role.TECNICO).stream().filter(t -> Objects.equals(t.getSupervisorId(), me.id())).forEach(t -> equipo.add(t.getId()));
        }
        return minutas.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> m.getTechnicianId() != null && !Objects.equals(m.getTechnicianId(), me.id()) && !equipo.contains(m.getTechnicianId()))
                .limit(60).map(m -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", m.getId());
                    r.put("local", m.getClientName());
                    r.put("tecnico", m.getTechnicianName());
                    r.put("estado", ReportesService.estadoDe(m));
                    r.put("fecha", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    return r;
                }).toList();
    }

    /** Pide acceso con un motivo. Va al supervisor del técnico o, si pide un supervisor, a los administradores. */
    @PostMapping
    @Transactional
    public Map<String, Object> pedir(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser me) {
        Minuta m = minutas.findById(((Number) req.get("minutaId")).longValue()).orElseThrow(() -> new IllegalArgumentException("Minuta no encontrada"));
        String motivo = String.valueOf(req.getOrDefault("motivo", "")).trim();
        if (motivo.isBlank()) {
            throw new IllegalArgumentException("Elige el motivo");
        }
        if (me.isAdmin() || Objects.equals(m.getTechnicianId(), me.id())) {
            throw new IllegalArgumentException("Ya puedes ver esa minuta");
        }
        SolicitudAcceso s = repo.save(SolicitudAcceso.builder().minutaId(m.getId()).minutaLocal(m.getClientName()).solicitanteId(me.id())
                .solicitanteNombre(me.fullName()).motivo(motivo).estado("pendiente").creadaEn(LocalDateTime.now()).build());
        List<UserEntity> aprobadores = new ArrayList<>();
        if (me.role() == Role.TECNICO) {
            users.findById(me.id()).map(UserEntity::getSupervisorId).flatMap(users::findById).ifPresent(aprobadores::add);
        }
        if (aprobadores.isEmpty()) {
            aprobadores.addAll(users.findByRole(Role.ADMINISTRADOR));
        }
        for (UserEntity a : aprobadores) {
            notificaciones.save(UserNotification.builder().userId(a.getId()).title("Solicitud de acceso")
                    .body(me.fullName() + " pide ver la minuta de " + m.getClientName() + ". Motivo: " + motivo + ".").type("ACCESO").refId(s.getId())
                    .minutaId(m.getId()).createdAt(LocalDateTime.now()).build());
        }
        bitacora.registrar(me, m.getId(), "aviso", "Pidió acceso a la minuta de " + m.getClientName() + " (" + motivo + ")");
        return Map.of("id", s.getId(), "estado", "pendiente");
    }

    /** Genera el código de 6 dígitos (24 h). Lo aprueba el supervisor del solicitante o un administrador. */
    @PostMapping("/{id}/generar")
    @Transactional
    public Map<String, Object> generar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser me) {
        SolicitudAcceso s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        puedeAprobar(s, me);
        if (!"pendiente".equals(s.getEstado())) {
            throw new IllegalArgumentException("La solicitud ya fue atendida");
        }
        s.setCodigo(String.valueOf(100000 + RND.nextInt(900000)));
        s.setEstado("aprobada");
        s.setAprobadorNombre(me.fullName());
        s.setVenceEn(LocalDateTime.now().plusHours(24));
        repo.save(s);
        notificaciones.save(UserNotification.builder().userId(s.getSolicitanteId()).title("Acceso aprobado")
                .body(me.fullName() + " aprobó tu acceso a la minuta de " + s.getMinutaLocal() + ". Pídele el código de 6 dígitos y escríbelo en Reportes. Vale 24 horas.")
                .type("ACCESO_APROBADO").refId(s.getId()).minutaId(s.getMinutaId()).createdAt(LocalDateTime.now()).build());
        bitacora.registrar(me, s.getMinutaId(), "aviso", "Aprobó el acceso de " + s.getSolicitanteNombre() + " a la minuta de " + s.getMinutaLocal());
        return Map.of("codigo", s.getCodigo(), "vence", s.getVenceEn().format(FMT), "solicitante", s.getSolicitanteNombre());
    }

    @PostMapping("/{id}/rechazar")
    @Transactional
    public Map<String, Object> rechazar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser me) {
        SolicitudAcceso s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        puedeAprobar(s, me);
        s.setEstado("rechazada");
        s.setAprobadorNombre(me.fullName());
        repo.save(s);
        notificaciones.save(UserNotification.builder().userId(s.getSolicitanteId()).title("Acceso rechazado")
                .body(me.fullName() + " no aprobó tu acceso a la minuta de " + s.getMinutaLocal() + ".").type("ACCESO_RECHAZADO").refId(s.getId()).createdAt(LocalDateTime.now()).build());
        bitacora.registrar(me, s.getMinutaId(), "aviso", "Rechazó el acceso de " + s.getSolicitanteNombre() + " a la minuta de " + s.getMinutaLocal());
        return Map.of("estado", "rechazada");
    }

    /** Anula un código ya generado (quien aprobó o un administrador). */
    @PostMapping("/{id}/anular")
    @Transactional
    public Map<String, Object> anular(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser me) {
        SolicitudAcceso s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
        puedeAprobar(s, me);
        s.setEstado("anulada");
        repo.save(s);
        bitacora.registrar(me, s.getMinutaId(), "aviso", "Anuló el acceso de " + s.getSolicitanteNombre() + " a la minuta de " + s.getMinutaLocal());
        return Map.of("estado", "anulada");
    }

    /** Quien pidió escribe el código: solo funciona para él, para esa minuta y mientras no pasen 24 horas. */
    @PostMapping("/canjear")
    @Transactional
    public Map<String, Object> canjear(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser me) {
        String codigo = String.valueOf(req.getOrDefault("codigo", "")).trim();
        SolicitudAcceso s = repo.findFirstBySolicitanteIdAndCodigoAndEstado(me.id(), codigo, "aprobada")
                .filter(x -> x.getVenceEn() != null && x.getVenceEn().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("El código no es válido o ya venció"));
        if (s.getCanjeadaEn() == null) {
            s.setCanjeadaEn(LocalDateTime.now());
            repo.save(s);
            bitacora.registrar(me, s.getMinutaId(), "aviso", "Abrió con código la minuta de " + s.getMinutaLocal());
        }
        return fila(s);
    }

    /** Accesos temporales vigentes del usuario (ya canjeados y sin vencer). */
    @GetMapping("/mios")
    public List<Map<String, Object>> mios(@AuthenticationPrincipal AuthenticatedUser me) {
        LocalDateTime ahora = LocalDateTime.now();
        return repo.findBySolicitanteIdOrderByCreadaEnDesc(me.id()).stream()
                .filter(s -> "aprobada".equals(s.getEstado()) && s.getCanjeadaEn() != null && s.getVenceEn() != null && s.getVenceEn().isAfter(ahora))
                .map(AccesoController::fila).toList();
    }

    /** Minuta en solo lectura mientras el acceso esté vigente (no se edita, descarga ni comparte). */
    @GetMapping("/{id}/minuta")
    public Map<String, Object> minuta(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser me) {
        SolicitudAcceso s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Acceso no encontrado"));
        if (!Objects.equals(s.getSolicitanteId(), me.id()) || !"aprobada".equals(s.getEstado()) || s.getCanjeadaEn() == null
                || s.getVenceEn() == null || s.getVenceEn().isBefore(LocalDateTime.now())) {
            throw new AccessDeniedException("El acceso no está vigente");
        }
        Minuta m = minutas.findById(s.getMinutaId()).orElseThrow(() -> new IllegalArgumentException("Minuta no encontrada"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("soloLectura", true);
        r.put("id", m.getId());
        r.put("clientName", m.getClientName());
        r.put("address", m.getAddress());
        r.put("technicianName", m.getTechnicianName());
        r.put("estado", ReportesService.estadoDe(m));
        r.put("version", m.versionOrOne());
        r.put("contentJson", m.getContentJson());
        r.put("vence", s.getVenceEn().format(FMT));
        return r;
    }

    private void puedeAprobar(SolicitudAcceso s, AuthenticatedUser me) {
        if (me.isAdmin()) {
            return;
        }
        boolean suyo = me.isSupervisor() && users.findById(s.getSolicitanteId()).map(u -> Objects.equals(u.getSupervisorId(), me.id())).orElse(false);
        if (!suyo) {
            throw new AccessDeniedException("Solo el supervisor del solicitante o un administrador atiende esta solicitud");
        }
    }

    private static Map<String, Object> fila(SolicitudAcceso s) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("accesoId", s.getId());
        r.put("minutaId", s.getMinutaId());
        r.put("local", s.getMinutaLocal());
        r.put("vence", s.getVenceEn() == null ? "" : s.getVenceEn().format(FMT));
        return r;
    }
}
