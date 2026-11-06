package com.upc.idbi.gateway.reportes;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.roles.RoleService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reportes (igual en la app y en la web). Pestañas por permisos del rol: General (m_all), Por técnico (r_tec_all) o
 * Mis técnicos (r_tec_mine) y Mis minutas (a_create). Los filtros recalculan la lista y los cuatro indicadores.
 */
@Service
@RequiredArgsConstructor
public class ReportesService {

    private final MinutaRepository minutas;
    private final UserRepository users;
    private final RoleService roles;

    public record Fila(Long id, LocalDateTime fecha, Long tecnicoId, String tecnico, String supervisor, String local, String rubro,
                       String estado, String criticidad, Integer puntaje, String esfuerzo, String plazo, Integer minutos, String tipo,
                       Long evaluacionId) {
    }

    public record Filtros(LocalDate desde, LocalDate hasta, Set<String> tecnico, Set<String> supervisor, Set<String> rubro, Set<String> estado,
                          Set<String> criticidad, Set<String> esfuerzo, Set<String> plazo) {
    }

    static String estadoDe(Minuta m) {
        if (m.getStatus() == MinutaStatus.VALIDADA) {
            return "Validada";
        }
        if (m.getStatus() == MinutaStatus.COMPLETA) {
            return "Completa";
        }
        return m.getRejectionReason() != null && !m.getRejectionReason().isBlank() ? "En revisión" : "Pendiente";
    }

    static String criticidadDe(Integer p) {
        return p == null ? "Sin análisis" : p < 60 ? "Riesgo" : p < 80 ? "Observado" : "Viable";
    }

    /** Plazo por nivel de esfuerzo (Plazos de la empresa): Básica mismo día, Intermedia 1 día, Compleja 2 días. */
    static String plazoDe(Minuta m, LocalDateTime ahora) {
        String e = m.getEsfuerzo() == null ? "Básica" : m.getEsfuerzo();
        LocalDateTime limite = m.getCreatedAt().toLocalDate().plusDays("Compleja".equals(e) ? 2 : "Intermedia".equals(e) ? 1 : 0).atTime(23, 59);
        boolean terminada = m.getStatus() != MinutaStatus.BORRADOR;
        LocalDateTime fin = terminada ? (m.getUpdatedAt() == null ? m.getCreatedAt() : m.getUpdatedAt()) : ahora;
        return fin.isAfter(limite) ? "En mora" : "A tiempo";
    }

    static boolean terminada(Fila f) {
        return Set.of("Completa", "Validada", "En revisión").contains(f.estado());
    }

    static boolean pasa(Fila f, Filtros q, String saltar) {
        LocalDate d = f.fecha().toLocalDate();
        if (!"fecha".equals(saltar) && ((q.desde() != null && d.isBefore(q.desde())) || (q.hasta() != null && d.isAfter(q.hasta())))) {
            return false;
        }
        Map<String, Function<Fila, String>> campos = Map.of("tecnico", Fila::tecnico, "supervisor", Fila::supervisor, "rubro", Fila::rubro, "estado", Fila::estado,
                "criticidad", Fila::criticidad, "esfuerzo", Fila::esfuerzo, "plazo", Fila::plazo);
        Map<String, Set<String>> sel = new LinkedHashMap<>();
        sel.put("tecnico", q.tecnico());
        sel.put("supervisor", q.supervisor());
        sel.put("rubro", q.rubro());
        sel.put("estado", q.estado());
        sel.put("criticidad", q.criticidad());
        sel.put("esfuerzo", q.esfuerzo());
        sel.put("plazo", q.plazo());
        for (Map.Entry<String, Set<String>> e : sel.entrySet()) {
            if (e.getKey().equals(saltar) || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            if (!e.getValue().contains(String.valueOf(campos.get(e.getKey()).apply(f)))) {
                return false;
            }
        }
        return true;
    }

    /** Indicadores de la lista filtrada: completadas, a tiempo, tiempo promedio y en riesgo. */
    static Map<String, Object> indicadores(List<Fila> L) {
        List<Fila> hechas = L.stream().filter(ReportesService::terminada).toList();
        long aTiempo = L.stream().filter(f -> "A tiempo".equals(f.plazo())).count();
        List<Integer> mins = hechas.stream().map(Fila::minutos).filter(Objects::nonNull).toList();
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("completadas", hechas.size());
        k.put("total", L.size());
        k.put("aTiempoPct", L.isEmpty() ? 0 : Math.round(aTiempo * 100.0 / L.size()));
        k.put("tiempoPromedioMin", mins.isEmpty() ? null : Math.round(mins.stream().mapToInt(Integer::intValue).average().orElse(0)));
        k.put("enRiesgo", L.stream().filter(f -> f.puntaje() != null && f.puntaje() < 60).count());
        return k;
    }

    static List<Map<String, Object>> porTecnico(List<Fila> todas, List<String> tecnicos, Map<String, String> supDe) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String n : tecnicos) {
            List<Fila> L = todas.stream().filter(f -> n.equals(f.tecnico())).toList();
            Map<String, Object> r = new LinkedHashMap<>(indicadores(L));
            r.put("tecnico", n);
            r.put("supervisor", supDe.getOrDefault(n, "—"));
            r.put("meta", Math.max(10, L.size()));
            r.put("enMora", L.stream().filter(f -> "En mora".equals(f.plazo())).count());
            r.put("devueltas", L.stream().filter(f -> "En revisión".equals(f.estado())).count());
            out.add(r);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reporte(AuthenticatedUser user, String vista, Filtros q) {
        RoleService.ReportProfile p = RoleService.profile(roles.forRole(user.role()));
        List<String> tabs = new ArrayList<>();
        if (p.all()) {
            tabs.add("general");
        }
        if (!"none".equals(p.tecs())) {
            tabs.add("tecnicos");
        }
        if (p.own()) {
            tabs.add("mis");
        }
        String v = vista == null || vista.isBlank() ? (tabs.isEmpty() ? null : tabs.get(0)) : vista;
        if (v == null || !tabs.contains(v)) {
            throw new AccessDeniedException("Tu rol no tiene esta vista de Reportes");
        }
        List<UserEntity> us = users.findAll();
        Map<Long, UserEntity> byId = us.stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
        Map<String, String> supDe = new LinkedHashMap<>();
        for (UserEntity u : us) {
            UserEntity s = u.getSupervisorId() == null ? null : byId.get(u.getSupervisorId());
            supDe.put(u.getFullName(), s == null ? "—" : s.getFullName());
        }
        LocalDateTime ahora = LocalDateTime.now();
        List<Fila> todas = minutas.findAllByOrderByCreatedAtDesc().stream().map(m -> new Fila(m.getId(), m.getCreatedAt(), m.getTechnicianId(),
                m.getTechnicianName(), supDe.getOrDefault(m.getTechnicianName(), "—"), m.getClientName(), m.getRubro() == null ? "—" : m.getRubro(),
                estadoDe(m), criticidadDe(m.getPuntaje()), m.getPuntaje(), m.getEsfuerzo() == null ? "Básica" : m.getEsfuerzo(), plazoDe(m, ahora),
                m.getMinutosElaboracion(), m.getTipo() == null ? "IMPLEMENTACION" : m.getTipo(), m.getEvaluationId())).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pestanas", tabs);
        out.put("vista", v);
        if ("tecnicos".equals(v)) {
            List<String> tecs = us.stream().filter(u -> u.getRole() == Role.TECNICO)
                    .filter(u -> "all".equals(p.tecs()) || Objects.equals(u.getSupervisorId(), user.id())).map(UserEntity::getFullName).toList();
            out.put("tecnicos", porTecnico(todas, tecs, supDe));
            out.put("plazosEmpresa", user.isAdmin());
            return out;
        }
        List<Fila> alcance = "mis".equals(v) ? todas.stream().filter(f -> Objects.equals(f.tecnicoId(), user.id())).toList() : todas;
        Filtros f2 = "mis".equals(v) ? new Filtros(q.desde(), q.hasta(), Set.of(), Set.of(), q.rubro(), q.estado(), q.criticidad(), q.esfuerzo(), q.plazo()) : q;
        List<Fila> filas = alcance.stream().filter(x -> pasa(x, f2, null)).toList();
        out.put("indicadores", indicadores(filas));
        out.put("minutas", filas);
        out.put("total", alcance.size());
        return out;
    }
}
