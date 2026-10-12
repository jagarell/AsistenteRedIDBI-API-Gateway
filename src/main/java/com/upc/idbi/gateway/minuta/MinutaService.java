package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.minuta.dto.MinutaRequest;
import com.upc.idbi.gateway.notification.MinutaNotifier;
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
    private final MinutaNotifier notifier;

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
        assertCanEdit(minuta, editor);
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
        boolean wasCompleted = minuta.getStatus() == MinutaStatus.COMPLETA;
        minuta.setStatus(MinutaStatus.COMPLETA);
        minuta.setUpdatedAt(LocalDateTime.now());
        Minuta saved = repository.save(minuta);
        if (!wasCompleted) {
            // Un push que falla nunca debe impedir completar la minuta (ver trySend).
            notifier.minutaCompleted(saved);
        }
        return saved;
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
        Minuta saved = repository.save(minuta);
        // Avisa al técnico que la hizo; un push que falla nunca impide validar.
        notifier.minutaValidated(saved);
        return saved;
    }

    /** La minuta de una evaluación; si todavía no existe se crea en BORRADOR con los datos de la evaluación. */
    @Transactional
    public Minuta ensureForEvaluation(Evaluation evaluation, AuthenticatedUser author) {
        return repository.findFirstByEvaluationIdOrderByIdDesc(evaluation.getId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            String name = evaluation.getRestaurantName() == null || evaluation.getRestaurantName().isBlank()
                    ? "Evaluación " + evaluation.getId() : evaluation.getRestaurantName().trim();
            return repository.save(Minuta.builder()
                    .evaluationId(evaluation.getId())
                    .clientName(name)
                    .address(evaluation.getAddress())
                    .contactName(evaluation.getContactName())
                    .contactPhone(evaluation.getPhone())
                    .technicianId(author == null ? null : author.id())
                    .technicianName(author == null ? null : author.fullName())
                    .status(MinutaStatus.BORRADOR)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });
    }

    /** Técnico: solo en BORRADOR. Supervisor: en BORRADOR y COMPLETA. Nadie edita una minuta VALIDADA. */
    public void assertCanEdit(Minuta minuta, AuthenticatedUser editor) {
        if (minuta.getStatus() == MinutaStatus.VALIDADA) {
            throw new IllegalArgumentException("La minuta ya fue validada y no puede editarse");
        }
        boolean supervisor = editor != null && editor.isSupervisor();
        if (minuta.getStatus() == MinutaStatus.COMPLETA && !supervisor) {
            throw new IllegalArgumentException("La minuta está completa: solo el supervisor puede editarla");
        }
    }

    /** Lo editado en "Datos principales" también se ve en la lista de minutas. */
    @Transactional
    @SuppressWarnings("unchecked")
    public void syncFromEditions(Minuta minuta, java.util.Map<String, Object> editions) {
        Object datos = editions.get("datos");
        if (!(datos instanceof java.util.Map<?, ?> map)) {
            return;
        }
        Object name = map.get("nombre");
        if (name instanceof String n && !n.isBlank()) {
            minuta.setClientName(n.trim());
        }
        Object address = map.get("dir");
        if (address instanceof String a && !a.isBlank()) {
            minuta.setAddress(a.trim());
        }
        Object phone = map.get("celular");
        if (phone instanceof String ph && !ph.isBlank()) {
            minuta.setContactPhone(ph.trim());
        }
        Object contact = map.get("encargado");
        if (contact instanceof String c && !c.isBlank()) {
            minuta.setContactName(c.trim());
        }
        minuta.setUpdatedAt(LocalDateTime.now());
        repository.save(minuta);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Minuta> findByEvaluation(Long evaluationId) {
        return repository.findFirstByEvaluationIdOrderByIdDesc(evaluationId);
    }

    private IllegalArgumentException notFound(Long id) {
        return new IllegalArgumentException("No existe la minuta con id " + id);
    }
}
