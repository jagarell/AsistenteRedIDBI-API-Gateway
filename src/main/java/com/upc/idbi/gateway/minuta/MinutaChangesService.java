package com.upc.idbi.gateway.minuta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actualizar una minuta ya generada con el Asistente IA («Actualiza la minuta de …: …»):
 * el servicio de IA arma el plan de cambios; al aplicarlo la minuta sube de versión, guarda el historial
 * y, si estaba Completa o Validada, vuelve a Borrador para pasar otra vez por la validación del supervisor.
 */
@Service
@RequiredArgsConstructor
public class MinutaChangesService {

    private final MinutaService minutaService;
    private final MinutaRepository repository;
    private final MinutaVersionRepository versions;
    private final FastApiClient fastApi;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.upc.idbi.gateway.bitacora.BitacoraService bitacora;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.upc.idbi.gateway.notification.UserNotificationRepository notificaciones;

    public Map<String, Object> plan(Long id, String texto) {
        Minuta m = minutaService.get(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("texto", texto);
        body.put("impresoras", printersOf(m));
        return fastApi.post("/minutas/cambios/plan", body);
    }

    /** Aplica los ítems del plan. Los criterios de todas las minutas nunca se aplican desde aquí. */
    public Minuta aplicar(Long id, List<Map<String, Object>> items, AuthenticatedUser user) {
        return aplicar(id, items, user, null);
    }

    /**
     * «Fue una nueva visita al local»: marcada, la minuta toma la fecha de la actualización y el historial dice «Nueva visita»;
     * desmarcada (o sin dato), conserva la fecha original.
     */
    @Transactional
    public Minuta aplicar(Long id, List<Map<String, Object>> items, AuthenticatedUser user, Boolean nuevaVisita) {
        Minuta m = minutaService.get(id);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("No hay cambios para aplicar");
        }
        Map<String, Object> content = read(m.getContentJson());
        Map<String, Object> soporte = m.isSoporte() ? read(m.getSoporteJson()) : null;
        List<Map<String, Object>> accesos = readList(m.getAccesosJson());
        List<String> resumen = new ArrayList<>();
        if (nuevaVisita != null) {
            if (nuevaVisita) {
                content.put("fechaVisita", java.time.LocalDate.now().toString());
                resumen.add("Nueva visita: la fecha de la minuta es " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            } else {
                resumen.add("Se mantiene la fecha de la visita");
            }
        }
        for (Map<String, Object> it : items) {
            String k = String.valueOf(it.get("k"));
            String t = String.valueOf(it.getOrDefault("t", ""));
            switch (k) {
                case "cred" -> {
                    @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) it.get("v");
                    accesos.removeIf(a -> String.valueOf(a.get("eq")).equals(String.valueOf(c.get("eq"))));
                    accesos.add(c);
                    resumen.add("Accesos y contraseñas: " + t);
                }
                case "router" -> {
                    content.put("routerDet", it.get("v"));
                    if (it.get("enc") != null) {
                        content.put("routerEncF", it.get("enc"));
                    }
                    resumen.add("Ubicación del router: " + t);
                }
                case "swport" -> {
                    content.put("puertosSwitch", it.get("v"));
                    resumen.add("Puertos del switch: " + t);
                }
                case "rec" -> {
                    add(soporte != null ? soporte : content, "recs", Map.of("t", t, "d", String.valueOf(it.getOrDefault("d", ""))));
                    resumen.add("Recomendación: " + t);
                }
                case "pasoC", "pasoT" -> {
                    add(soporte != null ? soporte : content, "pasos", Map.of("p", "pasoC".equals(k) ? "Cliente" : "ID", "t", t));
                    resumen.add(("pasoC".equals(k) ? "Paso del cliente: " : "Paso del equipo técnico: ") + t);
                }
                case "obs" -> {
                    if (soporte != null) {
                        add(soporte, "notas", t);
                    } else {
                        String prev = String.valueOf(content.getOrDefault("obsTec", ""));
                        content.put("obsTec", (prev.isBlank() ? "" : prev.trim() + " ") + t);
                    }
                    resumen.add("Observación de la actualización");
                }
                default -> { }
            }
        }
        versions.save(MinutaVersion.builder().minutaId(m.getId()).version(m.versionOrOne() + 1).resumen(String.join("\n", resumen))
                .contentJsonPrev(m.getContentJson()).authorName(user == null ? null : user.fullName()).createdAt(LocalDateTime.now()).build());
        m.setContentJson(write(content));
        if (soporte != null) {
            m.setSoporteJson(write(soporte));
        }
        m.setAccesosJson(write(accesos));
        m.setVersion(m.versionOrOne() + 1);
        // Doc 0.0.7 §12: si cambia el técnico, vuelve a Pendiente (hay que solicitar otra vez la validación); si cambia el supervisor o el
        // administrador, sigue por validar (Completa) y el técnico recibe el aviso.
        boolean gestor = user != null && (user.isSupervisor() || user.isAdmin()) && !java.util.Objects.equals(m.getTechnicianId(), user.id());
        if (gestor) {
            if (m.getStatus() == MinutaStatus.VALIDADA) {
                m.setStatus(MinutaStatus.COMPLETA);
                m.setValidatedById(null);
                m.setValidatedByName(null);
                m.setValidatedAt(null);
            }
        } else if (m.getStatus() == MinutaStatus.COMPLETA || m.getStatus() == MinutaStatus.VALIDADA) {
            m.setStatus(MinutaStatus.BORRADOR);
            m.setValidatedById(null);
            m.setValidatedByName(null);
            m.setValidatedAt(null);
        }
        m.setUpdatedAt(LocalDateTime.now());
        Minuta saved = repository.save(m);
        if (gestor && notificaciones != null && saved.getTechnicianId() != null) {
            String que = resumen.stream().filter(x -> !x.startsWith("Se mantiene") && !x.startsWith("Nueva visita")).findFirst().map(x -> ": " + x).orElse("");
            notificaciones.save(com.upc.idbi.gateway.notification.UserNotification.builder().userId(saved.getTechnicianId()).title("Minuta actualizada")
                    .body((user.isAdmin() ? "El administrador " : "El supervisor ") + user.fullName() + " actualizó la minuta a v" + saved.versionOrOne() + que)
                    .type("MINUTA_ACTUALIZADA").minutaId(saved.getId()).evaluationId(saved.getEvaluationId()).createdAt(LocalDateTime.now()).build());
        }
        if (bitacora != null) bitacora.registrar(user, saved.getId(), "editar", "Actualizó la minuta de " + saved.getClientName() + " a la v" + saved.versionOrOne() + " con el Asistente IA");
        return saved;
    }

    public List<MinutaVersion> historial(Long id) {
        return versions.findByMinutaIdOrderByVersionDesc(id);
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> target, String key, Object value) {
        List<Object> l = target.get(key) instanceof List<?> x ? new ArrayList<>((List<Object>) x) : new ArrayList<>();
        l.add(value);
        target.put(key, l);
    }

    private List<Map<String, Object>> printersOf(Minuta m) {
        Object p = read(m.getContentJson()).get("impresoras");
        if (p instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : l) {
                if (o instanceof Map<?, ?> x) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("name", x.get("name"));
                    r.put("con", x.get("con"));
                    out.add(r);
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> read(String json) {
        try {
            return json == null || json.isBlank() ? new LinkedHashMap<>() : mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readList(String json) {
        try {
            return json == null || json.isBlank() ? new ArrayList<>() : mapper.readValue(json, new TypeReference<ArrayList<Map<String, Object>>>() { });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar la minuta", e);
        }
    }
}
