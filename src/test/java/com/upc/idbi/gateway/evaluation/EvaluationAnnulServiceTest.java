package com.upc.idbi.gateway.evaluation;

import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationAnnulServiceTest {

    @Mock
    EvaluationRepository evaluations;

    @Mock
    MinutaRepository minutas;

    @InjectMocks
    EvaluationAnnulService service;

    private Evaluation evaluation(Long id, EvaluationStatus status) {
        return Evaluation.builder().id(id).restaurantName("Local").status(status).build();
    }

    @Test
    void anularUnBorradorLoDejaAnuladoYDescartaSusBorradoresDeMinuta() {
        when(evaluations.findById(1L)).thenReturn(Optional.of(evaluation(1L, EvaluationStatus.BORRADOR)));
        when(evaluations.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));

        Evaluation result = service.annul(1L);

        assertThat(result.getAnnulled()).isTrue();
        assertThat(result.getStatus()).isEqualTo(EvaluationStatus.BORRADOR);
        verify(minutas).deleteByEvaluationIdAndStatus(1L, MinutaStatus.BORRADOR);
    }

    @Test
    void noSeAnulaDosVeces() {
        Evaluation yaAnulada = evaluation(3L, EvaluationStatus.BORRADOR);
        yaAnulada.setAnnulled(true);
        when(evaluations.findById(3L)).thenReturn(Optional.of(yaAnulada));

        assertThatThrownBy(() -> service.annul(3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSeAnulaLoQueYaNoEsBorrador() {
        for (EvaluationStatus status : new EvaluationStatus[]{
                EvaluationStatus.COMPLETADO, EvaluationStatus.ENVIADO, EvaluationStatus.EN_ANALISIS}) {
            when(evaluations.findById(2L)).thenReturn(Optional.of(evaluation(2L, status)));
            assertThatThrownBy(() -> service.annul(2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("borrador");
        }
        verify(minutas, never()).deleteByEvaluationIdAndStatus(any(), any());
    }
}
