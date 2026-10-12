package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.auth.UserEntity;
import com.upc.idbi.gateway.auth.UserRepository;
import com.upc.idbi.gateway.minuta.Minuta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinutaNotifierTest {

    @Mock
    PushNotificationService push;

    @Mock
    UserRepository users;

    @Mock
    UserNotificationRepository inbox;

    @InjectMocks
    MinutaNotifier notifier;

    private UserEntity user(String token) {
        UserEntity u = new UserEntity();
        u.setFcmToken(token);
        return u;
    }

    @Test
    void minutaCompletadaAvisaATodosLosSupervisores() {
        Minuta minuta = Minuta.builder().id(3L).clientName("Rock & Burgers").technicianName("Yomira").build();
        when(users.findByRoleAndFcmTokenIsNotNull(Role.SUPERVISOR)).thenReturn(List.of(user("t1"), user("t2")));

        notifier.minutaCompleted(minuta);

        verify(push).trySend(eq("t1"), eq("Minuta por validar"), contains("Rock & Burgers"), anyMap());
        verify(push).trySend(eq("t2"), eq("Minuta por validar"), contains("Yomira"), anyMap());
    }

    @Test
    void laMinutaCompletadaQuedaEnLaBandejaDeCadaSupervisorAunSinDispositivo() {
        Minuta minuta = Minuta.builder().id(3L).evaluationId(9L).clientName("Rock").technicianName("Yomira").build();
        UserEntity sup = user(null);
        sup.setId(7L);
        when(users.findByRole(Role.SUPERVISOR)).thenReturn(List.of(sup));

        notifier.minutaCompleted(minuta);

        verify(inbox).save(org.mockito.ArgumentMatchers.argThat((UserNotification n) ->
                n.getUserId().equals(7L) && "MINUTA_COMPLETA".equals(n.getType()) && n.getMinutaId().equals(3L)
                        && n.getEvaluationId().equals(9L)));
    }

    @Test
    void alValidarSeAvisaAlTecnicoQueLaHizo() {
        Minuta minuta = Minuta.builder().id(3L).evaluationId(9L).clientName("Rock").technicianId(1L)
                .validatedByName("Ana Torres").build();
        UserEntity tecnico = user("tok");
        tecnico.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(tecnico));

        notifier.minutaValidated(minuta);

        verify(push).trySend(eq("tok"), eq("Minuta validada"), contains("Ana Torres"), anyMap());
        verify(inbox).save(org.mockito.ArgumentMatchers.argThat((UserNotification n) ->
                n.getUserId().equals(1L) && "MINUTA_VALIDADA".equals(n.getType())));
    }

    @Test
    void borradorPorVencerAvisaSoloAlTecnicoDuenio() {
        Minuta minuta = Minuta.builder().id(4L).clientName("Cliente").technicianId(1L).build();
        when(users.findById(1L)).thenReturn(Optional.of(user("tecnico-token")));
        when(push.trySend(eq("tecnico-token"), eq("Minuta por vencer"), contains("vence mañana"), anyMap())).thenReturn(true);

        assertThat(notifier.draftExpiring(minuta, 1)).isTrue();
        verify(users, never()).findByRoleAndFcmTokenIsNotNull(any());
    }

    @Test
    void sinTecnicoAsociadoNoAvisa() {
        Minuta minuta = Minuta.builder().id(5L).clientName("Cliente").build();

        assertThat(notifier.draftExpiring(minuta, 1)).isFalse();
    }
}
