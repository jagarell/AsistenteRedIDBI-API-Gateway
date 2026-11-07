package com.upc.idbi.gateway.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.auth.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Avisos configurables por rol (doc 0.0.7 §47). Lo que se marca vale igual en la app y en la web (misma cuenta). */
@Service
@RequiredArgsConstructor
public class AvisosService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final UserAvisosRepository repo;

    /** clave → texto, en el orden en que se muestran. */
    public static Map<String, String> opciones(Role rol) {
        Map<String, String> m = new LinkedHashMap<>();
        switch (rol) {
            case ADMINISTRADOR -> {
                m.put("completa", "Una minuta queda completa y espera validación");
                m.put("devuelta", "Un supervisor devuelve una minuta a revisión");
                m.put("codigo", "Alguien pide un código para su contraseña");
                m.put("mora", "Una minuta pasa su plazo (mora)");
            }
            case SUPERVISOR -> {
                m.put("tecEnvia", "Un técnico de tu equipo envía una minuta");
                m.put("mora", "Una minuta de tu equipo pasa su plazo");
                m.put("corrige", "Un técnico corrige una minuta que devolviste");
            }
            default -> {
                m.put("validan", "Validan una de tus minutas");
                m.put("devuelven", "Te devuelven una minuta a revisión");
                m.put("plazo", "Se acerca el plazo de una minuta");
            }
        }
        return m;
    }

    public Map<String, Boolean> estado(Long userId, Role rol) {
        Map<String, Boolean> guardado = new HashMap<>();
        repo.findById(userId).ifPresent(a -> {
            try {
                guardado.putAll(JSON.readValue(a.getJson(), new TypeReference<Map<String, Boolean>>() { }));
            } catch (Exception ignored) {
                // se usan los valores por defecto
            }
        });
        Map<String, Boolean> out = new LinkedHashMap<>();
        opciones(rol).keySet().forEach(k -> out.put(k, guardado.getOrDefault(k, true)));
        return out;
    }

    public boolean activo(Long userId, Role rol, String clave) {
        return userId == null || estado(userId, rol).getOrDefault(clave, true);
    }

    public Map<String, Boolean> guardar(Long userId, Role rol, Map<String, Object> cambios) {
        Map<String, Boolean> actual = estado(userId, rol);
        cambios.forEach((k, v) -> {
            if (actual.containsKey(k)) {
                actual.put(k, Boolean.parseBoolean(String.valueOf(v)));
            }
        });
        try {
            repo.save(UserAvisos.builder().userId(userId).json(JSON.writeValueAsString(actual)).build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudieron guardar los avisos");
        }
        return actual;
    }
}
