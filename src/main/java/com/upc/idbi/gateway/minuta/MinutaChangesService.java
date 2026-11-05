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

    public Map<String, Object> plan(Long id, String texto) {
        Minuta m = minutaService.get(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("texto", texto);
        body.put("impresoras", printersOf(m));
        return fastApi.post("/minutas/cambios/plan", body);
    }

    /** Aplica los ítems del plan. Los criterios de todas las minutas nunca se aplican desde aquí. */
    @Transactional
    public Minuta aplicar(Long id, List<Map<String, Object>> items, AuthenticatedUser user) {
        Minuta m = minutaService.get(id);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("No hay cambios para aplicar");
        }
        Map<String, Object> content = read(m.getContentJson());
        Map<String, Object> soporte = m.isSoporte() ? read(m.getSoporteJson()) : null;
        List<Map<String, Object>> accesos = readList(m.getAccesosJson());
        List<String> resumen = new ArrayList<>();
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
        if (m.getStatus() == MinutaStatus.COMPLETA || m.getStatus() == MinutaStatus.VALIDADA) {
            m.setStatus(MinutaStatus.BORRADOR);
            m.setValidatedById(null);
            m.setValidatedByName(null);
            m.setValidatedAt(null);
        }
        m.setUpdatedAt(LocalDateTime.now());
        return repository.save(m);
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
