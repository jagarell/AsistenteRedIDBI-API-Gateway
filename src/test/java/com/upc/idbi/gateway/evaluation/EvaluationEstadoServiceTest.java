package com.upc.idbi.gateway.evaluation;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationEstadoServiceTest {

    @Mock
    MinutaRepository minutas;

    @InjectMocks
    EvaluationEstadoService service;

    @Test
    void sinChatTerminadoNiMinutaEstaEnCurso() {
        Evaluation e = Evaluation.builder().id(1L).build();
        when(minutas.findFirstByEvaluationIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        assertThat(service.decorate(e).getMinutaStatus()).isEqualTo("EN_CURSO");
    }

    @Test
    void conChatTerminadoYSinMinutaEsBorrador() {
        Evaluation e = Evaluation.builder().id(2L).chatAnswersJson("{}").build();
        when(minutas.findFirstByEvaluationIdOrderByIdDesc(2L)).thenReturn(Optional.empty());
        assertThat(service.decorate(e).getMinutaStatus()).isEqualTo("BORRADOR");
    }

    @Test
    void conMinutaUsaSuEstadoYQuienLaValido() {
        Evaluation e = Evaluation.builder().id(3L).chatAnswersJson("{}").build();
        Minuta m = Minuta.builder().id(9L).status(MinutaStatus.VALIDADA).validatedByName("Ana").technicianName("Luis").build();
        when(minutas.findFirstByEvaluationIdOrderByIdDesc(3L)).thenReturn(Optional.of(m));
        Evaluation out = service.decorate(e);
        assertThat(out.getMinutaStatus()).isEqualTo("VALIDADA");
        assertThat(out.getMinutaId()).isEqualTo(9L);
        assertThat(out.getValidatedByName()).isEqualTo("Ana");
        assertThat(out.getTechnicianName()).isEqualTo("Luis");
    }
}
