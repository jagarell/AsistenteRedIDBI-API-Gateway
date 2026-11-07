package com.upc.idbi.gateway.inicio;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.reportes.ReportesService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Indicadores del Inicio por rol (doc 0.0.7 §20, §23, §27). No se reinician cada día: una minuta sigue contando hasta que se resuelve.
 * <ul>
 *   <li>Técnico: Pendientes · En revisión (rechazadas) · Esperando validación (Completas), solo las suyas.</li>
 *   <li>Supervisor: Por validar · Pendientes (de su equipo y suyas) · En riesgo (menos de 60 % y sin validar), de su equipo.</li>
 *   <li>Administrador: lo mismo que el supervisor, de toda la empresa.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/inicio")
@RequiredArgsConstructor
public class InicioController {

    private final MinutaRepository minutas;
    private final UserRepository users;
    private final com.upc.idbi.gateway.empresa.EmpresaService empresa;

    @GetMapping("/indicadores")
    public Map<String, Object> indicadores(@AuthenticationPrincipal AuthenticatedUser me) {
        List<Minuta> todas = empresa.contadas(minutas.findAllByOrderByCreatedAtDesc());
        List<Minuta> alcance;
        if (me.isAdmin()) {
            alcance = todas;
        } else if (me.isSupervisor()) {
            Set<Long> equipo = users.findByRole(Role.TECNICO).stream().filter(t -> Objects.equals(t.getSupervisorId(), me.id()))
                    .map(UserEntity::getId).collect(Collectors.toSet());
            equipo.add(me.id());
            alcance = todas.stream().filter(m -> m.getTechnicianId() != null && equipo.contains(m.getTechnicianId())).toList();
        } else {
            alcance = todas.stream().filter(m -> Objects.equals(m.getTechnicianId(), me.id())).toList();
        }
        List<Map<String, Object>> ind = new ArrayList<>();
        if (me.role() == Role.TECNICO) {
            ind.add(ind("pendientes", "Pendientes", alcance, "Pendiente", null));
            ind.add(ind("en_revision", "En revisión", alcance, "En revisión", null));
            ind.add(ind("esperando", "Esperando validación", alcance, "Completa", null));
        } else {
            ind.add(ind("por_validar", "Por validar", alcance, "Completa", null));
            ind.add(ind("pendientes", "Pendientes", alcance, "Pendiente", null));
            List<Minuta> riesgo = alcance.stream().filter(m -> !"Validada".equals(ReportesService.estadoDe(m)) && "Riesgo".equals(ReportesService.criticidadDe(m.getPuntaje()))).toList();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("clave", "en_riesgo");
            r.put("label", "En riesgo");
            r.put("cantidad", riesgo.size());
            r.put("criticidad", "Riesgo");
            r.put("minutaIds", riesgo.stream().map(Minuta::getId).toList());
            ind.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rol", me.role().name());
        out.put("indicadores", ind);
        return out;
    }

    private static Map<String, Object> ind(String clave, String label, List<Minuta> alcance, String estado, String criticidad) {
        List<Minuta> l = alcance.stream().filter(m -> estado.equals(ReportesService.estadoDe(m))).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("clave", clave);
        r.put("label", label);
        r.put("cantidad", l.size());
        r.put("estado", estado);
        r.put("minutaIds", l.stream().map(Minuta::getId).toList());
        return r;
    }
}
