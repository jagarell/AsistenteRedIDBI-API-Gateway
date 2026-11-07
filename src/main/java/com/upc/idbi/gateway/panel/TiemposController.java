package com.upc.idbi.gateway.panel;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.bitacora.BitacoraService;
import com.upc.idbi.gateway.empresa.EmpresaService;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tiempo por minuta: mes anterior vs mes actual (doc 0.0.7 §17) y «Editar plazos» (administrador).
 * Si el mes anterior no tiene minutas reales con tiempo (primer mes en producción), el «anterior» son los tiempos manuales
 * de «Editar plazos», es decir, cómo se trabajaba antes de la app.
 */
@RestController
@RequestMapping("/api/panel")
@RequiredArgsConstructor
public class TiemposController {

    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre"};

    private final MinutaRepository minutas;
    private final UserRepository users;
    private final EmpresaService empresa;
    private final BitacoraService bitacora;

    @GetMapping("/plazos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERVISOR')")
    public Map<String, Object> plazos() {
        Map<String, Object> p = new LinkedHashMap<>(empresa.plazos());
        p.put("metaTecnicoMin", 120);
        p.put("metaSupervisorHoras", 24);
        return p;
    }

    /** Editar plazos: minutos manuales por tipo de local y costo por hora. Solo el administrador. */
    @PutMapping("/plazos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> guardar(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, Object> actual = new LinkedHashMap<>(empresa.plazos());
        if (body.get("rubros") instanceof Map<?, ?> r) {
            Map<String, Object> rubros = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : r.entrySet()) {
                String nombre = String.valueOf(e.getKey()).trim();
                if (nombre.isEmpty()) {
                    continue;
                }
                int min = e.getValue() instanceof Number n ? n.intValue() : parse(String.valueOf(e.getValue()));
                if (min < 5 || min > 1440) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tiempo de «" + nombre + "» debe estar entre 5 y 1440 minutos");
                }
                rubros.put(nombre, min);
            }
            if (!rubros.isEmpty()) {
                actual.put("rubros", rubros);
            }
        }
        if (body.get("costoHora") != null) {
            double c = body.get("costoHora") instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(body.get("costoHora")));
            if (c <= 0 || c > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo por hora debe ser mayor que 0");
            }
            actual.put("costoHora", c);
        }
        empresa.guardarPlazos(actual);
        bitacora.registrar(user, null, "empresa", "Editó los plazos de la empresa");
        return plazos();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Tiempo por minuta del mes pedido (por defecto, el actual) frente al mes anterior. */
    @GetMapping("/tiempos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERVISOR')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> tiempos(@RequestParam(required = false) String mes, @AuthenticationPrincipal AuthenticatedUser me) {
        YearMonth actual = mes == null || mes.isBlank() ? YearMonth.now() : YearMonth.parse(mes);
        YearMonth anterior = actual.minusMonths(1);
        boolean admin = me.isAdmin();
        Set<Long> equipo = users.findByRole(Role.TECNICO).stream().filter(t -> admin || Objects.equals(t.getSupervisorId(), me.id()))
                .map(UserEntity::getId).collect(Collectors.toSet());
        List<Minuta> todas = empresa.contadas(minutas.findAllByOrderByCreatedAtDesc()).stream()
                .filter(m -> m.getMinutosElaboracion() != null && m.getCreatedAt() != null)
                .filter(m -> admin || (m.getTechnicianId() != null && equipo.contains(m.getTechnicianId())))
                .toList();

        Map<String, Double> real = promedios(todas, actual);
        Map<String, Double> previo = promedios(todas, anterior);
        boolean manual = previo.isEmpty();
        Map<String, Object> plazos = empresa.plazos();
        if (manual) {
            previo = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) plazos.get("rubros")).entrySet()) {
                previo.put(e.getKey(), ((Number) e.getValue()).doubleValue());
            }
        }
        TreeSet<String> rubros = new TreeSet<>(real.keySet());
        rubros.retainAll(manual ? previo.keySet() : previo.keySet());
        if (rubros.isEmpty()) {
            rubros.addAll(real.keySet());
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        double sumaMejora = 0;
        int n = 0;
        for (String r : rubros) {
            Double a = previo.get(r);
            Double c = real.get(r);
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("rubro", r);
            f.put("anteriorMin", a == null ? null : Math.round(a));
            f.put("actualMin", c == null ? null : Math.round(c));
            if (a != null && c != null && a > 0) {
                long pct = Math.round(100.0 * (a - c) / a);
                f.put("mejoraPct", pct);
                sumaMejora += pct;
                n++;
            } else {
                f.put("mejoraPct", null);
            }
            filas.add(f);
        }
        long minutasMes = todas.stream().filter(m -> YearMonth.from(m.getCreatedAt()).equals(actual)).count();
        double horasAhorradas = 0;
        for (Map<String, Object> f : filas) {
            if (f.get("anteriorMin") instanceof Long a && f.get("actualMin") instanceof Long c) {
                long cant = todas.stream().filter(m -> YearMonth.from(m.getCreatedAt()).equals(actual) && rubroDe(m).equals(f.get("rubro"))).count();
                horasAhorradas += Math.max(0, a - c) * cant / 60.0;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mes", actual.toString());
        out.put("mesNombre", MESES[actual.getMonthValue() - 1]);
        out.put("anterior", anterior.toString());
        out.put("anteriorNombre", MESES[anterior.getMonthValue() - 1]);
        out.put("anteriorOrigen", manual ? "manual" : "real");
        out.put("rubros", filas);
        out.put("mejoraPromedioPct", n == 0 ? null : Math.round(sumaMejora / n));
        out.put("minutasMes", minutasMes);
        out.put("horasAhorradas", Math.round(horasAhorradas * 10) / 10.0);
        out.put("solesAhorro", Math.round(horasAhorradas * empresa.costoHora()));
        out.put("costoHora", empresa.costoHora());
        return out;
    }

    private static String rubroDe(Minuta m) {
        return m.getRubro() == null || m.getRubro().isBlank() ? "Sin rubro" : m.getRubro();
    }

    private static Map<String, Double> promedios(List<Minuta> l, YearMonth ym) {
        return l.stream().filter(m -> YearMonth.from(m.getCreatedAt()).equals(ym))
                .collect(Collectors.groupingBy(TiemposController::rubroDe, TreeMap::new, Collectors.averagingInt(Minuta::getMinutosElaboracion)));
    }
}
