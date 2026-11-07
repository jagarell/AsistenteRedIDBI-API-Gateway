package com.upc.idbi.gateway.evaluation;

import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anular una evaluación: solo las que siguen en borrador. */
@Service
@RequiredArgsConstructor
public class EvaluationAnnulService {

    private final EvaluationRepository evaluations;
    private final MinutaRepository minutas;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.upc.idbi.gateway.bitacora.BitacoraService bitacora;

    /**
     * Marca la evaluación como anulada y descarta los borradores de minuta que salieron de ella
     * (una minuta COMPLETA o VALIDADA nunca se toca).
     */
    @Transactional
    public Evaluation annul(Long id) {
        Evaluation evaluation = evaluations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación con id " + id));
        if (evaluation.getStatus() != EvaluationStatus.BORRADOR || Boolean.TRUE.equals(evaluation.getAnnulled())) {
            throw new IllegalArgumentException("Solo se pueden anular las evaluaciones en borrador");
        }
        evaluation.setAnnulled(true);
        // Nada se borra: los borradores de minuta quedan archivados y el administrador los puede restaurar.
        for (com.upc.idbi.gateway.minuta.Minuta m : minutas.findByEvaluationIdAndStatus(id, MinutaStatus.BORRADOR)) {
            m.setArchivada(true);
            minutas.save(m);
            if (bitacora != null) {
                bitacora.registrar(null, m.getId(), "archivar", "Anuló el borrador de " + m.getClientName() + ": quedó archivado");
            }
        }
        return evaluations.save(evaluation);
    }
}
