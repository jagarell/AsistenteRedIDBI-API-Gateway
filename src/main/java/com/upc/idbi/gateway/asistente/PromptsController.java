package com.upc.idbi.gateway.asistente;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** GET/PUT de los prompts frecuentes del usuario (doc 0.0.7 §52): hasta 50, de hasta 600 caracteres, sin repetidos. */
@RestController
@RequestMapping("/api/asistente/prompts")
@RequiredArgsConstructor
public class PromptsController {

    private final PromptsFrecuentesRepository repo;
    private final ObjectMapper mapper;

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("prompts", leer(user.id()));
    }

    @PutMapping
    public Map<String, Object> put(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        List<String> l = req.get("prompts") instanceof List<?> x ? x.stream().map(String::valueOf).map(String::trim)
                .filter(t -> !t.isEmpty()).map(t -> t.length() > 600 ? t.substring(0, 600) : t).distinct().limit(50).collect(Collectors.toList()) : List.of();
        repo.save(PromptsFrecuentes.builder().userId(user.id()).json(mapper.writeValueAsString(l)).build());
        return Map.of("prompts", l);
    }

    private List<String> leer(Long userId) {
        try {
            return repo.findById(userId).map(p -> {
                try {
                    return mapper.readValue(p.getJson(), new TypeReference<List<String>>() {
                    });
                } catch (Exception e) {
                    return List.<String>of();
                }
            }).orElse(List.of());
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
