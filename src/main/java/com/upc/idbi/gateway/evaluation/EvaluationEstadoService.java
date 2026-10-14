package com.upc.idbi.gateway.evaluation;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Completa cada evaluación con el estado que muestran el Historial y el Inicio (ver {@link Evaluation#getMinutaStatus()}). */
@Service
@RequiredArgsConstructor
public class EvaluationEstadoService {

    private final MinutaRepository minutaRepository;

    @Transactional(readOnly = true)
    public List<Evaluation> decorate(List<Evaluation> evaluations) {
        evaluations.forEach(this::decorate);
        return evaluations;
    }

    @Transactional(readOnly = true)
    public Evaluation decorate(Evaluation e) {
        Minuta m = minutaRepository.findFirstByEvaluationIdOrderByIdDesc(e.getId()).orElse(null);
        if (m != null) {
            e.setMinutaStatus(m.getStatus().name());
            e.setMinutaId(m.getId());
            e.setValidatedByName(m.getValidatedByName());
            e.setTechnicianName(m.getTechnicianName());
        } else {
            boolean chatTerminado = e.getChatAnswersJson() != null && !e.getChatAnswersJson().isBlank();
            e.setMinutaStatus(chatTerminado ? "BORRADOR" : "EN_CURSO");
        }
        return e;
    }
}
