package com.upc.idbi.gateway.soporte;

import com.upc.idbi.gateway.minuta.dto.MinutaResponse;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Asistente IA › Nueva minuta › Soporte / incidencia. */
@RestController
@RequiredArgsConstructor
public class SoporteController {

    private final SoporteService service;

    @GetMapping("/api/clientes/antecedentes")
    public List<Map<String, Object>> antecedentes(@RequestParam String cliente) {
        return service.antecedentes(cliente);
    }

    @PostMapping("/api/asistente/soporte/evaluar")
    public Map<String, Object> evaluar(@RequestBody Map<String, Object> req) {
        return service.evaluar(req);
    }

    @PostMapping("/api/minutas/soporte")
    @ResponseStatus(HttpStatus.CREATED)
    public MinutaResponse crear(@RequestBody Map<String, Object> req, @AuthenticationPrincipal AuthenticatedUser user) {
        return MinutaResponse.from(service.crear(req, user));
    }
}
