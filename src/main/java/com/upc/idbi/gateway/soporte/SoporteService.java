package com.upc.idbi.gateway.soporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minuta de Soporte / incidencia: antecedentes del cliente, documento (lo arma el servicio de IA con las reglas S1–S4)
 * y guardado como una minuta más (Pendiente → Solicitar validación → supervisor), con versiones como la de implementación.
 */
@Service
@RequiredArgsConstructor
public class SoporteService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MinutaRepository repository;
    private final FastApiClient fastApi;
    private final ObjectMapper mapper;

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    /** Minutas e incidencias anteriores del cliente (por nombre del local). */
    public List<Map<String, Object>> antecedentes(String cliente) {
        String q = norm(cliente);
        List<Map<String, Object>> out = new ArrayList<>();
        if (q.isEmpty()) {
            return out;
        }
        for (Minuta m : repository.findAllByOrderByCreatedAtDesc()) {
            String n = norm(m.getClientName());
            if (n.contains(q) || q.contains(n)) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("minutaId", m.getId());
                r.put("tipo", m.isSoporte() ? "soporte" : "implementacion");
                r.put("fecha", m.getCreatedAt() == null ? "" : m.getCreatedAt().format(DMY));
                r.put("estado", m.getStatus().name());
                r.put("texto", m.isSoporte() ? motivo(m) : "");
                r.put("direccion", m.getAddress());
                r.put("rubro", m.getRubro());
                out.add(r);
            }
        }
        return out;
    }

    public Map<String, Object> evaluar(Map<String, Object> req) {
        return fastApi.post("/asistente/soporte/evaluar", req);
    }

    /** Redacta y guarda la minuta de soporte. Queda en BORRADOR (Pendiente) hasta que el técnico solicite la validación. */
    @Transactional
    public Minuta crear(Map<String, Object> req, AuthenticatedUser user) {
        Map<String, Object> body = new LinkedHashMap<>(req);
        String local = String.valueOf(body.getOrDefault("local", body.getOrDefault("cliente", ""))).trim();
        if (local.isEmpty()) {
            throw new IllegalArgumentException("Indica el cliente de la visita de soporte");
        }
        body.putIfAbsent("antecedentes", antecedentes(local));
        body.putIfAbsent("fecha", LocalDateTime.now().format(DMY));
        body.put("tecnico", user.fullName());
        Map<String, Object> doc = fastApi.post("/asistente/soporte/minuta", body);
        LocalDateTime now = LocalDateTime.now();
        Minuta m = Minuta.builder()
                .clientName(local)
                .address((String) body.get("direccion"))
                .technicianId(user.id())
                .technicianName(user.fullName())
                .status(MinutaStatus.BORRADOR)
                .tipo("SOPORTE")
                .version(1)
                .rubro((String) body.get("rubro"))
                .puntaje(doc.get("porcentaje") instanceof Number p ? p.intValue() : null)
                .esfuerzo("Básica")
                .estadoSoporte((String) doc.get("estadoCodigo"))
                .summary(String.valueOf(doc.getOrDefault("estado", "")))
                .contentJson(write(doc))
                .soporteJson(write(body))
                .accesosJson(write(body.getOrDefault("credenciales", List.of())))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return repository.save(m);
    }

    private String motivo(Minuta m) {
        try {
            Object v = mapper.readValue(m.getSoporteJson() == null ? "{}" : m.getSoporteJson(), Map.class).get("motivo");
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar la minuta de soporte", e);
        }
    }
}
