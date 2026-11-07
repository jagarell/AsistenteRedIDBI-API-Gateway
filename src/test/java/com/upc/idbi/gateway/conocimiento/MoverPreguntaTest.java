package com.upc.idbi.gateway.conocimiento;

import com.upc.idbi.gateway.auth.Role;
import com.upc.idbi.gateway.common.FastApiClient;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Doc 0.0.7 §11: subir o bajar una pregunta dentro de su sección; ↑ en la primera y ↓ en la última no hacen nada. */
class MoverPreguntaTest {

    private final List<PreguntaFlujo> tabla = new ArrayList<>();
    private ReglasController controller;
    private final AuthenticatedUser admin = new AuthenticatedUser(1L, "a@x.pe", "Admin", Role.ADMINISTRADOR);

    private static PreguntaFlujo p(String id, String seccion, Integer orden) {
        return PreguntaFlujo.builder().id(id).seccionId(seccion).texto("t " + id).orden(orden).build();
    }

    @BeforeEach
    void preparar() {
        tabla.clear();
        tabla.addAll(List.of(p("P98", "B", 1), p("P99", "B", 2), p("P97", "B", 3), p("P50", "E", null)));
        PreguntaFlujoRepository repo = mock(PreguntaFlujoRepository.class);
        when(repo.findAll()).thenAnswer(i -> new ArrayList<>(tabla));
        when(repo.findById(anyString())).thenAnswer(i -> tabla.stream().filter(x -> x.getId().equals(i.getArgument(0))).findFirst());
        when(repo.saveAll(anyIterable())).thenAnswer(i -> i.getArgument(0));
        controller = new ReglasController(mock(ReglaEmpresaRepository.class), repo, mock(FastApiClient.class));
    }

    private List<String> ids(List<PreguntaFlujo> l, String seccion) {
        return l.stream().filter(x -> seccion.equals(x.getSeccionId())).map(PreguntaFlujo::getId).toList();
    }

    @Test
    void subirUnaPreguntaLaPasaAntesDeLaAnterior() {
        assertEquals(List.of("P99", "P98", "P97"), ids(controller.mover("P99", Map.of("dir", "arriba"), admin), "B"));
    }

    @Test
    void bajarYDeshacerDejaElOrdenOriginal() {
        controller.mover("P98", Map.of("dir", "abajo"), admin);
        assertEquals(List.of("P98", "P99", "P97"), ids(controller.mover("P98", Map.of("dir", "arriba"), admin), "B"));
    }

    @Test
    void laPrimeraNoSubeYLaUltimaNoBaja() {
        assertEquals(List.of("P98", "P99", "P97"), ids(controller.mover("P98", Map.of("dir", "arriba"), admin), "B"));
        assertEquals(List.of("P98", "P99", "P97"), ids(controller.mover("P97", Map.of("dir", "abajo"), admin), "B"));
    }

    @Test
    void moverNoMezclaSecciones() {
        List<PreguntaFlujo> r = controller.mover("P99", Map.of("dir", "abajo"), admin);
        assertEquals(List.of("P50"), ids(r, "E"));
    }
}
