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
    private final com.upc.idbi.gateway.bitacora.BitacoraService bitacora;

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
        Minuta m = service.create(request, user);
        bitacora.registrar(user, m.getId(), "crear", "Creó la minuta de " + m.getClientName());
        return MinutaResponse.from(m);
    }

    @PutMapping("/{id}")
    public MinutaResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MinutaRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Minuta m = service.update(id, request, user);
        bitacora.registrar(user, id, "editar", "Editó la minuta de " + m.getClientName());
        return MinutaResponse.from(m);
    }

    /** Marca la minuta como COMPLETA (lista para validación del supervisor). */
    @PostMapping("/{id}/completar")
    public MinutaResponse complete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        Minuta m = service.complete(id);
        bitacora.registrar(user, id, "solicitar", "Solicitó la validación de la minuta de " + m.getClientName());
        return MinutaResponse.from(m);
    }

    /** Valida una minuta completa. Sólo supervisores. */
    @PostMapping("/{id}/validar")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRADOR')")
    public MinutaResponse validate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser supervisor
    ) {
        Minuta m = service.validate(id, supervisor);
        bitacora.registrar(supervisor, id, "validar", "Validó la minuta de " + m.getClientName());
        return MinutaResponse.from(m);
    }

    /** Devuelve una minuta completa al técnico con el motivo. Sólo supervisores. */
    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRADOR')")
    public MinutaResponse reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            @AuthenticationPrincipal AuthenticatedUser supervisor
    ) {
        Minuta m = service.reject(id, request.reason(), supervisor);
        bitacora.registrar(supervisor, id, "rechazar", "Rechazó la minuta de " + m.getClientName() + ": " + request.reason());
        return MinutaResponse.from(m);
    }

    public record RejectRequest(String reason) {
    }
}
