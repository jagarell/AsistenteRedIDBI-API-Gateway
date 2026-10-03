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

    /**
     * Marca la evaluación como ANULADA y descarta los borradores de minuta que salieron de ella
     * (una minuta COMPLETA o VALIDADA nunca se toca).
     */
    @Transactional
    public Evaluation annul(Long id) {
        Evaluation evaluation = evaluations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación con id " + id));
        if (evaluation.getStatus() != EvaluationStatus.BORRADOR) {
            throw new IllegalArgumentException("Solo se pueden anular las evaluaciones en borrador");
        }
        evaluation.setStatus(EvaluationStatus.ANULADA);
        minutas.deleteByEvaluationIdAndStatus(id, MinutaStatus.BORRADOR);
        return evaluations.save(evaluation);
    }
}
