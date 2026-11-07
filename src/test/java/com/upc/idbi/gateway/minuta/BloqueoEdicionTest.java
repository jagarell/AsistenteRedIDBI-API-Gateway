package com.upc.idbi.gateway.minuta;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.notification.MinutaNotifier;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Doc 0.0.7 §12: mientras el supervisor edita, el técnico ve «En edición por el supervisor» y no puede editar. */
class BloqueoEdicionTest {

    private final MinutaService servicio = new MinutaService(mock(MinutaRepository.class), mock(MinutaNotifier.class));
    private final AuthenticatedUser tecnico = new AuthenticatedUser(10L, "t@x.pe", "Técnico", Role.TECNICO);
    private final AuthenticatedUser supervisor = new AuthenticatedUser(20L, "s@x.pe", "Lucía Torres", Role.SUPERVISOR);

    private static Minuta minuta(Long por, LocalDateTime desde) {
        return Minuta.builder().id(1L).clientName("Local").status(MinutaStatus.BORRADOR).technicianId(10L)
                .bloqueadoPorId(por).bloqueadoPorNombre("Lucía Torres").bloqueadoDesde(desde).build();
    }

    @Test
    void unBloqueoRecienPuestoEstaVigente() {
        assertTrue(minuta(20L, LocalDateTime.now()).bloqueada());
    }

    @Test
    void unBloqueoDeMasDe30MinutosYaNoCuenta() {
        assertFalse(minuta(20L, LocalDateTime.now().minusMinutes(31)).bloqueada());
    }

    @Test
    void sinBloqueoNoHayNada() {
        assertFalse(minuta(null, null).bloqueada());
    }

    @Test
    void elTecnicoNoEditaMientrasElSupervisorEdita() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.assertCanEdit(minuta(20L, LocalDateTime.now()), tecnico));
        assertEquals(423, e.getStatusCode().value());
        assertTrue(e.getReason().contains("En edición por Lucía Torres"));
    }

    @Test
    void quienBloqueaPuedeSeguirYSinBloqueoElTecnicoEdita() {
        assertDoesNotThrow(() -> servicio.assertNoBloqueada(minuta(20L, LocalDateTime.now()), supervisor));
        assertDoesNotThrow(() -> servicio.assertCanEdit(minuta(null, null), tecnico));
        assertDoesNotThrow(() -> servicio.assertCanEdit(minuta(20L, LocalDateTime.now().minusHours(1)), tecnico));
    }
}
