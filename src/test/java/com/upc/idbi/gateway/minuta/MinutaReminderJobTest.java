package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.notification.MinutaNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinutaReminderJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 9, 0);

    @Mock
    MinutaRepository repository;

    @Mock
    MinutaNotifier notifier;

    @InjectMocks
    MinutaReminderJob job;

    @BeforeEach
    void config() {
        ReflectionTestUtils.setField(job, "draftTtlDays", 7L);
        ReflectionTestUtils.setField(job, "warnDaysBefore", 2L);
    }

    private Minuta borrador(long id, LocalDateTime createdAt) {
        return Minuta.builder().id(id).clientName("Cliente " + id).status(MinutaStatus.BORRADOR)
                .technicianId(1L).createdAt(createdAt).build();
    }

    @Test
    void avisaCuandoFaltanDosDiasOMenosYMarcaElAviso() {
        // Creada hace 5 días y 1 hora: vence en ~1 día 23 h => faltan 1 día completo.
        Minuta porVencer = borrador(1L, NOW.minusDays(5).minusHours(1));
        when(repository.findByStatusAndExpiryReminderSentAtIsNull(MinutaStatus.BORRADOR)).thenReturn(List.of(porVencer));
        when(notifier.draftExpiring(any(Minuta.class), anyLong())).thenReturn(true);

        int sent = job.remind(NOW);

        assertThat(sent).isEqualTo(1);
        assertThat(porVencer.getExpiryReminderSentAt()).isEqualTo(NOW);
        verify(repository).save(porVencer);
    }

    @Test
    void noAvisaSiTodaviaFaltaMucho() {
        Minuta reciente = borrador(2L, NOW.minusDays(1));
        when(repository.findByStatusAndExpiryReminderSentAtIsNull(MinutaStatus.BORRADOR)).thenReturn(List.of(reciente));

        assertThat(job.remind(NOW)).isZero();

        verify(notifier, never()).draftExpiring(any(Minuta.class), anyLong());
        assertThat(reciente.getExpiryReminderSentAt()).isNull();
    }

    @Test
    void avisaUnBorradorYaVencidoQueNuncaSeAviso() {
        Minuta vencida = borrador(3L, NOW.minusDays(12));
        when(repository.findByStatusAndExpiryReminderSentAtIsNull(MinutaStatus.BORRADOR)).thenReturn(List.of(vencida));
        when(notifier.draftExpiring(any(Minuta.class), anyLong())).thenReturn(true);

        assertThat(job.remind(NOW)).isEqualTo(1);
        verify(notifier).draftExpiring(vencida, 0L);
    }

    @Test
    void siElPushNoSalePuedeReintentarseMasTarde() {
        Minuta porVencer = borrador(4L, NOW.minusDays(6));
        when(repository.findByStatusAndExpiryReminderSentAtIsNull(MinutaStatus.BORRADOR)).thenReturn(List.of(porVencer));
        when(notifier.draftExpiring(any(Minuta.class), anyLong())).thenReturn(false);

        assertThat(job.remind(NOW)).isZero();

        assertThat(porVencer.getExpiryReminderSentAt()).isNull();
        verify(repository, never()).save(any(Minuta.class));
    }
}
