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
import java.util.function.Function;
import java.util.stream.Collectors;
import com.upc.idbi.gateway.reportes.ReportesService;

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

    private final MinutaRepository minutas;
    private final UserRepository users;
    private final com.upc.idbi.gateway.empresa.EmpresaService empresa;
    private final com.upc.idbi.gateway.bitacora.BitacoraRepository bitacoraRepo;

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

        List<Minuta> todas = empresa.contadas(minutas.findAllByOrderByCreatedAtDesc()).stream()
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
        out.put("ahorro", Map.of("horas", horas, "soles", horas * empresa.costoHora(), "solesPorHora", empresa.costoHora()));
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

    // ------------------------------------------------------------------ Panel v2 (doc 0.0.7 §17, §43)

    private static final String[] MES_ABR = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    /**
     * Un solo filtro general (hoy, ayer, 7, 14, mes, todo o rango desde/hasta) para todo el Panel: indicadores, por día, estados, tiempos,
     * alertas, negocios, avance del equipo, por validar primero y actividad reciente. El supervisor ve su equipo; el administrador, todo.
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERVISOR')")
    public Map<String, Object> resumen(@RequestParam(defaultValue = "hoy") String periodo,
                                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @AuthenticationPrincipal AuthenticatedUser me) {
        boolean admin = me.isAdmin();
        List<UserEntity> tecnicos = users.findByRole(Role.TECNICO).stream()
                .filter(t -> admin || Objects.equals(t.getSupervisorId(), me.id()))
                .sorted(Comparator.comparing(UserEntity::getFullName, String.CASE_INSENSITIVE_ORDER)).toList();
        Set<Long> equipoIds = tecnicos.stream().map(UserEntity::getId).collect(Collectors.toSet());
        if (!admin) {
            equipoIds.add(me.id());
        }
        List<Minuta> alcance = empresa.contadas(minutas.findAllByOrderByCreatedAtDesc()).stream()
                .filter(m -> admin || (m.getTechnicianId() != null && equipoIds.contains(m.getTechnicianId()))).toList();

        LocalDate hoy = LocalDate.now();
        LocalDate d, h;
        String etiqueta;
        switch (periodo) {
            case "ayer" -> { d = hoy.minusDays(1); h = d; etiqueta = "Ayer"; }
            case "7" -> { d = hoy.minusDays(6); h = hoy; etiqueta = "7 días"; }
            case "14" -> { d = hoy.minusDays(13); h = hoy; etiqueta = "14 días"; }
            case "mes" -> { d = hoy.withDayOfMonth(1); h = hoy; etiqueta = "Este mes"; }
            case "todo" -> {
                d = alcance.stream().filter(m -> m.getCreatedAt() != null).map(m -> m.getCreatedAt().toLocalDate()).min(Comparator.naturalOrder()).orElse(hoy);
                h = hoy; etiqueta = "Todo";
            }
            case "rango" -> {
                d = desde == null ? hoy : desde; h = hasta == null ? hoy : hasta;
                if (h.isBefore(d)) { LocalDate t = d; d = h; h = t; }
                etiqueta = "Rango de fechas";
            }
            default -> { d = hoy; h = hoy; etiqueta = "Hoy"; periodo = "hoy"; }
        }
        final LocalDate ini = d, fin = h;
        long dias = java.time.temporal.ChronoUnit.DAYS.between(ini, fin) + 1;
        LocalDate antFin = ini.minusDays(1), antIni = antFin.minusDays(dias - 1);
        List<Minuta> en = alcance.stream().filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(ini) && !m.getCreatedAt().toLocalDate().isAfter(fin)).toList();
        long anterior = alcance.stream().filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(antIni) && !m.getCreatedAt().toLocalDate().isAfter(antFin)).count();

        Function<String, Long> cuenta = est -> en.stream().filter(m -> est.equals(ReportesService.estadoDe(m))).count();
        long pend = cuenta.apply("Pendiente"), comp = cuenta.apply("Completa"), rev = cuenta.apply("En revisión"), val = cuenta.apply("Validada");
        long riesgoPorValidar = en.stream().filter(m -> "Completa".equals(ReportesService.estadoDe(m)) && "Riesgo".equals(ReportesService.criticidadDe(m.getPuntaje()))).count();
        long terminadas = comp + rev + val;
        double horas = terminadas * HORAS_POR_MINUTA;

        Map<String, Object> ind = new LinkedHashMap<>();
        ind.put("total", en.size());
        ind.put("totalAnterior", anterior);
        ind.put("porValidar", comp);
        ind.put("porValidarRiesgo", riesgoPorValidar);
        ind.put("pendientes", pend);
        ind.put("enRevision", rev);
        ind.put("validadas", val);
        ind.put("validadasPct", en.isEmpty() ? 0 : Math.round(100.0 * val / en.size()));
        ind.put("horasAhorradas", horas);

        // Por día: la unidad cambia con el filtro (hora, día, semana, mes, año).
        List<Minuta> hechas = en.stream().filter(m -> !"Pendiente".equals(ReportesService.estadoDe(m))).toList();
        String unidad = ("hoy".equals(periodo) || "ayer".equals(periodo)) ? "hora" : dias <= 14 ? "dia" : dias <= 62 ? "semana" : dias <= 366 ? "mes" : "anio";
        Map<String, Long> barras = new LinkedHashMap<>();
        switch (unidad) {
            case "hora" -> { for (int i = 0; i < 24; i++) barras.put(String.format("%02d h", i), 0L); }
            case "dia" -> { for (LocalDate x = ini; !x.isAfter(fin); x = x.plusDays(1)) barras.put(String.format("%02d/%02d", x.getDayOfMonth(), x.getMonthValue()), 0L); }
            case "semana" -> { for (LocalDate x = ini; !x.isAfter(fin); x = x.plusDays(7)) barras.put(String.format("%02d/%02d", x.getDayOfMonth(), x.getMonthValue()), 0L); }
            case "mes" -> { for (java.time.YearMonth x = java.time.YearMonth.from(ini); !x.isAfter(java.time.YearMonth.from(fin)); x = x.plusMonths(1)) barras.put(MES_ABR[x.getMonthValue() - 1] + " " + (x.getYear() % 100), 0L); }
            default -> { for (int y = ini.getYear(); y <= fin.getYear(); y++) barras.put(String.valueOf(y), 0L); }
        }
        for (Minuta m : hechas) {
            LocalDateTime t = m.getCreatedAt();
            LocalDate x = t.toLocalDate();
            String k = switch (unidad) {
                case "hora" -> String.format("%02d h", t.getHour());
                case "dia" -> String.format("%02d/%02d", x.getDayOfMonth(), x.getMonthValue());
                case "semana" -> { LocalDate ws = ini.plusDays(7 * (java.time.temporal.ChronoUnit.DAYS.between(ini, x) / 7)); yield String.format("%02d/%02d", ws.getDayOfMonth(), ws.getMonthValue()); }
                case "mes" -> MES_ABR[x.getMonthValue() - 1] + " " + (x.getYear() % 100);
                default -> String.valueOf(x.getYear());
            };
            barras.merge(k, 1L, Long::sum);
        }
        Map<String, Object> porDia = new LinkedHashMap<>();
        porDia.put("unidad", unidad);
        porDia.put("barras", barras.entrySet().stream().map(e -> Map.<String, Object>of("etiqueta", e.getKey(), "cantidad", e.getValue())).toList());
        porDia.put("total", hechas.size());
        porDia.put("promedio", barras.isEmpty() ? 0 : Math.round(10.0 * hechas.size() / barras.size()) / 10.0);

        // Estados: la suma es igual al total del anillo.
        List<Map<String, Object>> estados = new ArrayList<>();
        for (var e : new String[][]{{"Pendiente"}, {"Completa"}, {"En revisión"}, {"Validada"}}) {
            long c = cuenta.apply(e[0]);
            estados.add(Map.of("estado", e[0], "cantidad", c, "pct", en.isEmpty() ? 0 : Math.round(100.0 * c / en.size())));
        }

        // Tiempos por rubro (minutos de elaboración) y ahorro estimado.
        Map<String, List<Minuta>> porRubro = en.stream().collect(Collectors.groupingBy(m -> m.getRubro() == null || m.getRubro().isBlank() ? "Sin rubro" : m.getRubro(), TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> rubros = new ArrayList<>();
        porRubro.forEach((rb, l) -> {
            OptionalDouble prom = l.stream().filter(m -> m.getMinutosElaboracion() != null).mapToInt(Minuta::getMinutosElaboracion).average();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("rubro", rb);
            f.put("minutas", l.size());
            f.put("minutosPromedio", prom.isPresent() ? Math.round(prom.getAsDouble()) : null);
            rubros.add(f);
        });
        Map<String, Object> tiempos = new LinkedHashMap<>();
        tiempos.put("rubros", rubros);
        tiempos.put("horasAhorradas", horas);
        tiempos.put("solesAhorro", horas * empresa.costoHora());
        tiempos.put("solesPorHora", empresa.costoHora());
        tiempos.put("metaMinutos", 120);

        // Alertas: cada una abre la consulta en el Asistente IA.
        LocalDateTime hace24 = LocalDateTime.now().minusHours(24);
        long riesgo = en.stream().filter(m -> !"Validada".equals(ReportesService.estadoDe(m)) && "Riesgo".equals(ReportesService.criticidadDe(m.getPuntaje()))).count();
        long sin24 = en.stream().filter(m -> "Completa".equals(ReportesService.estadoDe(m)) && (m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt()).isBefore(hace24)).count();
        long mora = en.stream().filter(m -> !"Validada".equals(ReportesService.estadoDe(m)) && "En mora".equals(ReportesService.plazoDe(m, LocalDateTime.now()))).count();
        List<Map<String, Object>> alertas = List.of(
                Map.of("texto", "Minutas en riesgo (menos de 60 %, sin validar)", "cantidad", riesgo, "consulta", "reporte de minutas en riesgo " + etiqueta.toLowerCase()),
                Map.of("texto", "Por validar hace más de 24 horas", "cantidad", sin24, "consulta", "minutas por validar"),
                Map.of("texto", "Fuera de plazo (en mora) sin validar", "cantidad", mora, "consulta", "reporte de minutas incompletas " + etiqueta.toLowerCase()));

        List<Map<String, Object>> negocios = porRubro.entrySet().stream().map(e -> Map.<String, Object>of("rubro", e.getKey(), "cantidad", e.getValue().size()))
                .sorted((a, b) -> Integer.compare((Integer) b.get("cantidad"), (Integer) a.get("cantidad"))).toList();

        // Avance y tiempos del equipo: por técnico, minutas por estado y horas promedio por minuta (meta 2 h).
        List<Map<String, Object>> equipo = new ArrayList<>();
        for (UserEntity t : tecnicos) {
            List<Minuta> suyas = en.stream().filter(m -> t.getId().equals(m.getTechnicianId())).toList();
            OptionalDouble prom = suyas.stream().filter(m -> m.getMinutosElaboracion() != null).mapToInt(Minuta::getMinutosElaboracion).average();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("tecnicoId", t.getId());
            f.put("tecnico", t.getFullName());
            f.put("pendiente", suyas.stream().filter(m -> "Pendiente".equals(ReportesService.estadoDe(m))).count());
            f.put("completa", suyas.stream().filter(m -> "Completa".equals(ReportesService.estadoDe(m))).count());
            f.put("enRevision", suyas.stream().filter(m -> "En revisión".equals(ReportesService.estadoDe(m))).count());
            f.put("validada", suyas.stream().filter(m -> "Validada".equals(ReportesService.estadoDe(m))).count());
            f.put("horasPromedio", prom.isPresent() ? Math.round(prom.getAsDouble() / 6.0) / 10.0 : null);
            equipo.add(f);
        }

        List<Map<String, Object>> primero = alcance.stream().filter(m -> "Completa".equals(ReportesService.estadoDe(m)))
                .sorted(Comparator.comparingInt(m -> m.getPuntaje() == null ? 0 : m.getPuntaje())).limit(5)
                .map(m -> Map.<String, Object>of("id", m.getId(), "local", m.getClientName(), "tecnico", String.valueOf(m.getTechnicianName()),
                        "puntaje", m.getPuntaje() == null ? 0 : m.getPuntaje(), "evaluationId", m.getEvaluationId() == null ? -1L : m.getEvaluationId())).toList();

        Set<Long> idsAlcance = alcance.stream().map(Minuta::getId).collect(Collectors.toSet());
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
        List<Map<String, Object>> actividad = bitacoraRepo.findTop200ByOrderByTsDesc().stream()
                .filter(b -> admin || (b.getMinutaId() != null && idsAlcance.contains(b.getMinutaId())) || Objects.equals(b.getUsuarioId(), me.id()))
                .limit(10).map(b -> Map.<String, Object>of("ts", b.getTs().format(fmt), "usuario", String.valueOf(b.getUsuarioNombre()), "accion", String.valueOf(b.getAccion()))).toList();

        Map<String, Object> per = new LinkedHashMap<>();
        per.put("clave", periodo);
        per.put("desde", ini.toString());
        per.put("hasta", fin.toString());
        per.put("etiqueta", etiqueta);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodo", per);
        out.put("indicadores", ind);
        out.put("porDia", porDia);
        out.put("estados", estados);
        out.put("tiempos", tiempos);
        out.put("alertas", alertas);
        out.put("negocios", negocios);
        out.put("equipo", equipo);
        out.put("metaHorasTecnico", 2);
        out.put("porValidarPrimero", primero);
        out.put("actividad", actividad);
        return out;
    }
}
