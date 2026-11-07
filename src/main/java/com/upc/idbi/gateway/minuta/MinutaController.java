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
        bitacora.registrar(user, m.getId(), "crear", "Creó la minuta de " + m.getClientName() + " con el Chat IA (preguntas guiadas)");
        return MinutaResponse.from(m);
    }

    @PutMapping("/{id}")
    public MinutaResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MinutaRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Minuta antes = service.get(id);
        String nombre0 = antes.getClientName(), dir0 = antes.getAddress(), contacto0 = antes.getContactName() + "|" + antes.getContactPhone();
        String notas0 = antes.getNotes(), topo0 = antes.getTopologyJson(), cont0 = antes.getContentJson();
        Minuta m = service.update(id, request, user);
        java.util.List<String> partes = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(nombre0, m.getClientName()) || !java.util.Objects.equals(dir0, m.getAddress())
                || !java.util.Objects.equals(contacto0, m.getContactName() + "|" + m.getContactPhone())) {
            partes.add("Datos del local: nombre, razón social, RUC, dirección o contacto");
        }
        if (!java.util.Objects.equals(notas0, m.getNotes())) {
            partes.add("Observaciones del técnico, viabilidad o método de pago");
        }
        if (!java.util.Objects.equals(topo0, m.getTopologyJson())) {
            partes.add("Topología de red: cambió equipos o conexiones");
        }
        if (!java.util.Objects.equals(cont0, m.getContentJson())) {
            partes.add("Propuesta técnica: equipamiento, diagnóstico o propuesta recomendada");
        }
        bitacora.registrar(user, id, "editar", "Editó la minuta de " + m.getClientName() + (partes.isEmpty() ? "" : " — " + String.join("; ", partes)));
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
