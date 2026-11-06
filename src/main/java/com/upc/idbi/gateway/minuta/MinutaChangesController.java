package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.minuta.dto.MinutaResponse;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Actualizar una minuta con el Asistente IA: plan de cambios, aplicar (nueva versión) e historial. */
@RestController
@RequestMapping("/api/minutas/{id}")
@RequiredArgsConstructor
public class MinutaChangesController {

    private final MinutaChangesService service;

    @PostMapping("/cambios/plan")
    public Map<String, Object> plan(@PathVariable Long id, @RequestBody PlanRequest req) {
        return service.plan(id, req.texto());
    }

    @PostMapping("/cambios/aplicar")
    public MinutaResponse aplicar(@PathVariable Long id, @RequestBody AplicarRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        return MinutaResponse.from(service.aplicar(id, req.items(), user));
    }

    @GetMapping("/versiones")
    public List<MinutaVersion> versiones(@PathVariable Long id) {
        return service.historial(id);
    }

    public record PlanRequest(String texto) {
    }

    public record AplicarRequest(List<Map<String, Object>> items) {
    }
}
