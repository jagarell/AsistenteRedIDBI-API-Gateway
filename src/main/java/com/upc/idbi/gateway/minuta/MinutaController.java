package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.minuta.dto.MinutaRequest;
import com.upc.idbi.gateway.minuta.dto.MinutaResponse;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de minutas.
 *
 * <ul>
 *     <li>Listar/ver/crear/editar: cualquier usuario autenticado (técnico o
 *     supervisor). Todas las minutas son visibles para todos, incluidos los
 *     borradores.</li>
 *     <li>Validar: sólo SUPERVISOR ({@code @PreAuthorize}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/minutas")
@RequiredArgsConstructor
public class MinutaController {

    private final MinutaService service;

    @GetMapping
    public List<MinutaResponse> list(
            @RequestParam(required = false) MinutaStatus status,
            @RequestParam(required = false) Long technicianId
    ) {
        return service.list(status, technicianId).stream()
                .map(MinutaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public MinutaResponse get(@PathVariable Long id) {
        return MinutaResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MinutaResponse create(
            @Valid @RequestBody MinutaRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return MinutaResponse.from(service.create(request, user));
    }

    @PutMapping("/{id}")
    public MinutaResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MinutaRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return MinutaResponse.from(service.update(id, request, user));
    }

    /** Marca la minuta como COMPLETA (lista para validación del supervisor). */
    @PostMapping("/{id}/completar")
    public MinutaResponse complete(@PathVariable Long id) {
        return MinutaResponse.from(service.complete(id));
    }

    /** Valida una minuta completa. Sólo supervisores. */
    @PostMapping("/{id}/validar")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public MinutaResponse validate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser supervisor
    ) {
        return MinutaResponse.from(service.validate(id, supervisor));
    }
}
