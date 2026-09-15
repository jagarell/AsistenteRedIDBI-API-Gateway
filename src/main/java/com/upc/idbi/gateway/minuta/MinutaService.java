package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.minuta.dto.MinutaRequest;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MinutaService {

    private final MinutaRepository repository;

    /**
     * Lista minutas. Todas son visibles para técnicos y supervisores (incluidas
     * las que están en borrador). Filtros opcionales por estado y por técnico.
     */
    @Transactional(readOnly = true)
    public List<Minuta> list(MinutaStatus status, Long technicianId) {
        if (status != null) {
            return repository.findByStatusOrderByCreatedAtDesc(status);
        }
        if (technicianId != null) {
            return repository.findByTechnicianIdOrderByCreatedAtDesc(technicianId);
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Minuta get(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public Minuta create(MinutaRequest request, AuthenticatedUser author) {
        LocalDateTime now = LocalDateTime.now();
        Minuta minuta = Minuta.builder()
                .evaluationId(request.evaluationId())
                .clientName(request.clientName().trim())
                .address(request.address())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .notes(request.notes())
                .technicianId(author.id())
                .technicianName(author.fullName())
                .status(MinutaStatus.BORRADOR)
                .summary(request.summary())
                .topologyJson(request.topologyJson())
                .contentJson(request.contentJson())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return repository.save(minuta);
    }

    /**
     * Actualiza una minuta. Cualquier técnico o supervisor puede editarla
     * (incluso si la creó otro), salvo que ya esté VALIDADA (bloqueada).
     *
     * <p>Es una actualización parcial: sólo se sobrescriben los campos que
     * llegan con valor en el request (no nulos). Así, por ejemplo, la pantalla
     * de edición de contacto puede enviar sólo clientName/address/contacto
     * sin borrar el resumen/topología/equipo ya generados por el chat.</p>
     */
    @Transactional
    public Minuta update(Long id, MinutaRequest request, AuthenticatedUser editor) {
        Minuta minuta = get(id);
        if (minuta.getStatus() == MinutaStatus.VALIDADA) {
            throw new IllegalArgumentException(
                    "La minuta ya fue validada y no puede editarse"
            );
        }
        minuta.setClientName(request.clientName().trim());
        if (request.address() != null) {
            minuta.setAddress(request.address());
        }
        if (request.contactName() != null) {
            minuta.setContactName(request.contactName());
        }
        if (request.contactPhone() != null) {
            minuta.setContactPhone(request.contactPhone());
        }
        if (request.notes() != null) {
            minuta.setNotes(request.notes());
        }
        if (request.summary() != null) {
            minuta.setSummary(request.summary());
        }
        if (request.topologyJson() != null) {
            minuta.setTopologyJson(request.topologyJson());
        }
        if (request.contentJson() != null) {
            minuta.setContentJson(request.contentJson());
        }
        if (request.evaluationId() != null) {
            minuta.setEvaluationId(request.evaluationId());
        }
        minuta.setUpdatedAt(LocalDateTime.now());
        return repository.save(minuta);
    }

    /** Marca una minuta como COMPLETA (lista para validación). */
    @Transactional
    public Minuta complete(Long id) {
        Minuta minuta = get(id);
        if (minuta.getStatus() == MinutaStatus.VALIDADA) {
            throw new IllegalArgumentException(
                    "La minuta ya fue validada"
            );
        }
        minuta.setStatus(MinutaStatus.COMPLETA);
        minuta.setUpdatedAt(LocalDateTime.now());
        return repository.save(minuta);
    }

    /**
     * Valida una minuta completa. Sólo debe invocarse para un usuario con rol
     * SUPERVISOR (la autorización se aplica en el controlador con @PreAuthorize;
     * aquí se refuerza la precondición de estado).
     */
    @Transactional
    public Minuta validate(Long id, AuthenticatedUser supervisor) {
        Minuta minuta = get(id);
        if (minuta.getStatus() != MinutaStatus.COMPLETA) {
            throw new IllegalArgumentException(
                    "Solo se puede validar una minuta en estado COMPLETA"
            );
        }
        minuta.setStatus(MinutaStatus.VALIDADA);
        minuta.setValidatedById(supervisor.id());
        minuta.setValidatedByName(supervisor.fullName());
        minuta.setValidatedAt(LocalDateTime.now());
        minuta.setUpdatedAt(LocalDateTime.now());
        return repository.save(minuta);
    }

    private IllegalArgumentException notFound(Long id) {
        return new IllegalArgumentException("No existe la minuta con id " + id);
    }
}
