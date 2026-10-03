package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.minuta.dto.MinutaRequest;
import com.upc.idbi.gateway.notification.MinutaNotifier;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinutaServiceTest {

    @Mock
    MinutaRepository repository;

    @Mock
    MinutaNotifier notifier;

    @InjectMocks
    MinutaService service;

    private final AuthenticatedUser tecnico =
            new AuthenticatedUser(1L, "tecnico@idbi.pe", "Tecnico Uno", Role.TECNICO);
    private final AuthenticatedUser supervisor =
            new AuthenticatedUser(9L, "supervisor@idbi.pe", "Supervisor", Role.SUPERVISOR);

    private MinutaRequest request() {
        return new MinutaRequest(null, "Cliente Demo", "Av. 1", "Contacto Demo", "999999999", null, "resumen", "{}", "{}");
    }

    @Test
    void crearDejaLaMinutaEnBorradorYAsignaAlTecnico() {
        when(repository.save(any(Minuta.class))).thenAnswer(inv -> inv.getArgument(0));

        Minuta minuta = service.create(request(), tecnico);

        assertThat(minuta.getStatus()).isEqualTo(MinutaStatus.BORRADOR);
        assertThat(minuta.getTechnicianId()).isEqualTo(1L);
        assertThat(minuta.getTechnicianName()).isEqualTo("Tecnico Uno");
    }

    @Test
    void validarRequiereEstadoCompleta() {
        Minuta borrador = Minuta.builder()
                .id(5L).clientName("Cliente").status(MinutaStatus.BORRADOR).build();
        when(repository.findById(5L)).thenReturn(java.util.Optional.of(borrador));

        assertThatThrownBy(() -> service.validate(5L, supervisor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETA");
    }

    @Test
    void validarUnaMinutaCompletaLaMarcaValidadaYRegistraSupervisor() {
        Minuta completa = Minuta.builder()
                .id(6L).clientName("Cliente").status(MinutaStatus.COMPLETA).build();
        when(repository.findById(6L)).thenReturn(java.util.Optional.of(completa));
        when(repository.save(any(Minuta.class))).thenAnswer(inv -> inv.getArgument(0));

        Minuta result = service.validate(6L, supervisor);

        assertThat(result.getStatus()).isEqualTo(MinutaStatus.VALIDADA);
        assertThat(result.getValidatedById()).isEqualTo(9L);
        assertThat(result.getValidatedByName()).isEqualTo("Supervisor");
        assertThat(result.getValidatedAt()).isNotNull();
    }

    @Test
    void noSePuedeEditarUnaMinutaValidada() {
        Minuta validada = Minuta.builder()
                .id(7L).clientName("Cliente").status(MinutaStatus.VALIDADA).build();
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(validada));

        assertThatThrownBy(() -> service.update(7L, request(), tecnico))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validada");
    }

    @Test
    void completarAvisaALosSupervisoresUnaSolaVez() {
        Minuta borrador = Minuta.builder()
                .id(8L).clientName("Cliente").status(MinutaStatus.BORRADOR).build();
        when(repository.findById(8L)).thenReturn(java.util.Optional.of(borrador));
        when(repository.save(any(Minuta.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(8L);
        verify(notifier).minutaCompleted(borrador);

        // Volver a completarla no repite el aviso.
        service.complete(8L);
        verify(notifier, org.mockito.Mockito.times(1)).minutaCompleted(any(Minuta.class));
    }

    @Test
    void validarNoEnviaAvisoDeCompletada() {
        Minuta completa = Minuta.builder()
                .id(9L).clientName("Cliente").status(MinutaStatus.COMPLETA).build();
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(completa));
        when(repository.save(any(Minuta.class))).thenAnswer(inv -> inv.getArgument(0));

        service.validate(9L, supervisor);

        verify(notifier, never()).minutaCompleted(any(Minuta.class));
    }
}
