package com.upc.idbi.gateway.notification;

import com.upc.idbi.gateway.auth.Role;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Doc 0.0.7 §47: «Avisarme cuando…» según el rol; todo activo por defecto y lo marcado se conserva. */
class AvisosServiceTest {

    private final Map<Long, UserAvisos> tabla = new HashMap<>();
    private final AvisosService service;

    AvisosServiceTest() {
        UserAvisosRepository repo = mock(UserAvisosRepository.class);
        when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(tabla.get((Long) i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> {
            UserAvisos a = i.getArgument(0);
            tabla.put(a.getUserId(), a);
            return a;
        });
        service = new AvisosService(repo);
    }

    @Test
    void cadaRolTieneSusOpciones() {
        assertTrue(AvisosService.opciones(Role.ADMINISTRADOR).containsKey("codigo"));
        assertTrue(AvisosService.opciones(Role.SUPERVISOR).containsKey("tecEnvia"));
        assertTrue(AvisosService.opciones(Role.TECNICO).containsKey("validan"));
        assertFalse(AvisosService.opciones(Role.TECNICO).containsKey("codigo"));
    }

    @Test
    void sinConfigurarTodoEstaActivo() {
        assertTrue(service.activo(1L, Role.SUPERVISOR, "tecEnvia"));
        assertTrue(service.estado(1L, Role.TECNICO).values().stream().allMatch(v -> v));
    }

    @Test
    void loQueSeDesmarcaSeConservaYNoAceptaClavesDeOtroRol() {
        service.guardar(2L, Role.SUPERVISOR, Map.of("mora", false, "codigo", false));
        assertFalse(service.activo(2L, Role.SUPERVISOR, "mora"));
        assertTrue(service.activo(2L, Role.SUPERVISOR, "tecEnvia"));
        assertEquals(3, service.estado(2L, Role.SUPERVISOR).size());
    }
}
