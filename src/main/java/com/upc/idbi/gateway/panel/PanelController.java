package com.upc.idbi.gateway.panel;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Panel de gestión (doc 0.0.7 §14): indicadores para decidir. El administrador ve a todos los técnicos; el supervisor,
 * solo a los de su equipo. «Por supervisor» es solo del administrador.
 */
@RestController
@RequestMapping("/api/panel")
@RequiredArgsConstructor
public class PanelController {

    /** Meta diaria de minutas terminadas por técnico y horas que se estima que ahorra cada minuta. */
    private static final int META_DIARIA = 3;
    private static final double HORAS_POR_MINUTA = 1.5;
    private static final double SOLES_POR_HORA = 25.0;

    private final MinutaRepository minutas;
    private final UserRepository users;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERVISOR')")
    public Map<String, Object> panel(@RequestParam(defaultValue = "14") int dias, @AuthenticationPrincipal AuthenticatedUser me) {
        int rango = dias <= 7 ? 7 : dias <= 14 ? 14 : 30;
        LocalDateTime desde = LocalDate.now().minusDays(rango - 1L).atStartOfDay();
        boolean admin = me.isAdmin();

        List<UserEntity> tecnicos = users.findByRole(Role.TECNICO).stream()
                .filter(t -> admin || Objects.equals(t.getSupervisorId(), me.id()))
                .sorted(Comparator.comparing(UserEntity::getFullName, String.CASE_INSENSITIVE_ORDER)).toList();
        Set<Long> equipo = tecnicos.stream().map(UserEntity::getId).collect(Collectors.toSet());

        List<Minuta> todas = minutas.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> admin || (m.getTechnicianId() != null && equipo.contains(m.getTechnicianId())))
                .toList();
        List<Minuta> enRango = todas.stream().filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().isBefore(desde)).toList();

        long borrador = enRango.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR && m.getRejectionReason() == null).count();
        long rechazadas = enRango.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR && m.getRejectionReason() != null).count();
        long porValidar = enRango.stream().filter(m -> m.getStatus() == MinutaStatus.COMPLETA).count();
        long validadas = enRango.stream().filter(m -> m.getStatus() == MinutaStatus.VALIDADA).count();
        long terminadas = porValidar + validadas;
        double horas = terminadas * HORAS_POR_MINUTA;

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("minutas", enRango.size());
        kpi.put("porValidar", porValidar);
        kpi.put("pendientes", borrador);
        kpi.put("rechazadas", rechazadas);
        kpi.put("validadas", validadas);
        kpi.put("horasAhorradas", horas);

        LocalDate hoy = LocalDate.now();
        List<Map<String, Object>> avance = new ArrayList<>();
        for (UserEntity t : tecnicos) {
            List<Minuta> suyas = todas.stream().filter(m -> t.getId().equals(m.getTechnicianId())).toList();
            long hechasHoy = suyas.stream().filter(m -> m.getStatus() != MinutaStatus.BORRADOR && fecha(m).equals(hoy)).count();
            long enCurso = suyas.stream().filter(m -> m.getStatus() == MinutaStatus.BORRADOR).count();
            avance.add(Map.of("tecnico", t.getFullName(), "terminadas", hechasHoy, "enCurso", enCurso, "meta", META_DIARIA));
        }

        Map<String, Long> porDia = new TreeMap<>();
        for (int i = 0; i < rango; i++) porDia.put(LocalDate.now().minusDays(rango - 1L - i).toString(), 0L);
        enRango.forEach(m -> porDia.merge(m.getCreatedAt().toLocalDate().toString(), 1L, Long::sum));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dias", rango);
        out.put("kpi", kpi);
        out.put("avanceHoy", avance);
        out.put("metaDiaria", META_DIARIA);
        out.put("porDia", porDia);
        out.put("porEstado", Map.of("Borrador", borrador, "Rechazadas", rechazadas, "Por validar", porValidar, "Validadas", validadas));
        out.put("ahorro", Map.of("horas", horas, "soles", horas * SOLES_POR_HORA, "solesPorHora", SOLES_POR_HORA));
        if (admin) {
            List<Map<String, Object>> porSup = new ArrayList<>();
            for (UserEntity s : users.findByRole(Role.SUPERVISOR)) {
                Set<Long> suyos = users.findByRole(Role.TECNICO).stream().filter(t -> s.getId().equals(t.getSupervisorId()))
                        .map(UserEntity::getId).collect(Collectors.toSet());
                long pend = todas.stream().filter(m -> m.getStatus() == MinutaStatus.COMPLETA && suyos.contains(m.getTechnicianId())).count();
                porSup.add(Map.of("supervisor", s.getFullName(), "tecnicos", suyos.size(), "porValidar", pend));
            }
            out.put("porSupervisor", porSup);
            out.put("usuarios", users.count());
        }
        return out;
    }

    private static LocalDate fecha(Minuta m) {
        LocalDateTime t = m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt();
        return t.toLocalDate();
    }
}
