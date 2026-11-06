package com.upc.idbi.gateway.conocimiento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
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

/**
 * «¿Qué aprendiste hoy?» (solo administrador): reúne las visitas del día (soporte, información y evaluación), pide al servicio
 * de IA los casos y propuestas, y guarda lo que el administrador aprueba: reglas, preguntas y fichas de la base de conocimiento.
 */
@Service
@RequiredArgsConstructor
public class AprendizajeService {

    private final MinutaRepository minutas;
    private final ReglaEmpresaRepository reglas;
    private final PreguntaFlujoRepository preguntas;
    private final FichaConocimientoRepository fichas;
    private final FastApiClient fastApi;
    private final ObjectMapper mapper;

    static void soloAdmin(AuthenticatedUser u) {
        if (u == null || !u.isAdmin()) {
            throw new AccessDeniedException("Solo el administrador revisa lo aprendido y decide si se agregan reglas o preguntas.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> hoy(AuthenticatedUser user) {
        soloAdmin(user);
        LocalDate d = LocalDate.now();
        List<Map<String, Object>> visitas = new ArrayList<>();
        List<Map<String, String>> notas = new ArrayList<>();
        for (Minuta m : minutas.findAllByOrderByCreatedAtDesc()) {
            LocalDateTime t = m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt();
            if (t == null || !t.toLocalDate().equals(d)) {
                continue;
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", "m" + m.getId());
            v.put("local", m.getClientName());
            v.put("tecnico", m.getTechnicianName());
            if (m.isSoporte()) {
                Map<?, ?> s = read(m.getSoporteJson());
                v.put("tipo", "soporte");
                v.put("caso", s.get("motivo"));
                v.put("solucion", String.join(" ", strings(s.get("hechos"))));
                v.put("texto", s.get("motivo") + " " + String.join(" ", strings(s.get("hechos"))));
            } else {
                Map<?, ?> c = read(m.getContentJson());
                Object obs = c.get("obsTec");
                v.put("tipo", obs == null ? "evaluacion" : "informacion");
                v.put("texto", obs == null ? "" : obs.toString());
                if (obs != null) {
                    notas.add(Map.of("t", obs.toString(), "local", m.getClientName()));
                }
            }
            visitas.add(v);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rol", "adm");
        body.put("visitas", visitas);
        body.put("reglasExistentes", reglas.findAll().stream().map(ReglaEmpresa::getId).toList());
        body.put("notas", notas);
        body.put("totalReglas", reglas.findAll().stream().filter(ReglaEmpresa::isActiva).count());
        body.put("totalMinutas", minutas.count());
        body.put("fichas", fichas.count());
        return fastApi.post("/aprendizaje/hoy", body);
    }

    /** «Procede»: el asistente agrega las reglas (activas) y las preguntas; queda registrado que lo aprobó el administrador. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> aplicar(AuthenticatedUser user, List<Map<String, Object>> casos, String solo) {
        soloAdmin(user);
        Map<String, Object> r = fastApi.post("/aprendizaje/aplicar", Map.of("rol", "adm", "casos", casos, "solo", solo == null ? "" : solo));
        List<String> hechas = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> x : (List<Map<String, Object>>) r.getOrDefault("reglas", List.of())) {
            String id = String.valueOf(x.get("id"));
            if (!reglas.existsById(id)) {
                reglas.save(ReglaEmpresa.builder().id(id).nombre(str(x.get("t"))).tipo(str(x.get("tipo"))).activa(true).texto(str(x.get("d")))
                        .cuando(str(x.get("c"))).preguntaId(str(x.get("q"))).valor(str(x.get("v"))).origen("asistente_ia")
                        .creadaPor(user.fullName() + " (aprobada; la creó el Asistente IA)").creadaEn(now).build());
                hechas.add("Regla " + id);
            }
        }
        for (Map<String, Object> x : (List<Map<String, Object>>) r.getOrDefault("preguntas", List.of())) {
            String id = String.valueOf(x.get("id"));
            if (!preguntas.existsById(id)) {
                preguntas.save(PreguntaFlujo.builder().id(id).seccionId(str(x.get("seccionId"))).seccion(str(x.get("seccion"))).texto(str(x.get("texto")))
                        .tipo(str(x.get("tipo"))).opciones(String.join("|", strings(x.get("opciones")))).cuando(str(x.get("cuando"))).origen("asistente_ia")
                        .creadaPor(user.fullName() + " (aprobada; la agregó el Asistente IA)").creadaEn(now).build());
                hechas.add("Pregunta " + id);
            }
        }
        return Map.of("agregadas", hechas);
    }

    /** Guarda el conocimiento y devuelve la ficha de aprendizaje en PDF (base64). */
    @Transactional
    public Map<String, Object> fichas(AuthenticatedUser user, List<Map<String, Object>> casos, String empresa) {
        soloAdmin(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rol", "adm");
        body.put("casos", casos);
        body.put("admin", user.fullName());
        body.put("empresa", empresa == null || empresa.isBlank() ? "IDBI" : empresa);
        body.put("inicio", (int) fichas.count() + 1);
        Map<String, Object> r = fastApi.post("/conocimiento/fichas", body);
        @SuppressWarnings("unchecked") List<Map<String, Object>> fs = (List<Map<String, Object>>) r.getOrDefault("fichas", List.of());
        for (Map<String, Object> f : fs) {
            fichas.save(FichaConocimiento.builder().codigo(str(f.get("codigo"))).tipo(str(f.get("tipo"))).local(str(f.get("local"))).tecnico(str(f.get("tecnico")))
                    .caso(str(f.get("caso"))).aprendizaje(str(f.get("aprendizaje"))).fichaJson(write(f)).guardadaPor(user.fullName())
                    .guardadaEn(LocalDateTime.now()).build());
        }
        return r;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        }
        return out;
    }

    private Map<?, ?> read(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
