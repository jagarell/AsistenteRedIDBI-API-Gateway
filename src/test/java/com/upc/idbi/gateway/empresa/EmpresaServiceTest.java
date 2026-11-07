package com.upc.idbi.gateway.empresa;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmpresaServiceTest {

    private static Minuta minuta(MinutaStatus s, String motivo, String etapa, Boolean visible) {
        return Minuta.builder().id(1L).clientName("Local").status(s).rejectionReason(motivo).etapa(etapa).visible(visible).build();
    }

    private static EmpresaService con(Empresa e) {
        EmpresaRepository repo = mock(EmpresaRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        return new EmpresaService(repo);
    }

    @Test
    void enDemoTodoCuentaYSeVe() {
        EmpresaService s = con(Empresa.builder().id(1L).nombre("IDBI").build());
        assertFalse(s.enProduccion());
        List<Minuta> l = List.of(minuta(MinutaStatus.BORRADOR, null, null, null), minuta(MinutaStatus.VALIDADA, null, null, null));
        assertEquals(2, s.contadas(l).size());
    }

    @Test
    void enProduccionLasMinutasDemoNoCuentanYLasDemoEnBorradorNoSeVen() {
        EmpresaService s = con(Empresa.builder().id(1L).nombre("ACME").produccionDesde(LocalDateTime.now()).build());
        Minuta demoValidada = minuta(MinutaStatus.VALIDADA, null, "demo", true);
        Minuta demoBorrador = minuta(MinutaStatus.BORRADOR, null, "demo", false);
        Minuta nueva = minuta(MinutaStatus.BORRADOR, null, "produccion", null);
        assertTrue(s.enProduccion());
        assertEquals(List.of(nueva), s.contadas(List.of(demoValidada, demoBorrador, nueva)));
        assertTrue(s.visible(demoValidada));
        assertFalse(s.visible(demoBorrador));
    }

    @Test
    void alPasarAProduccionSeConservanCompletasEnRevisionYValidadas() {
        assertTrue(EmpresaService.sobreviveAProduccion(minuta(MinutaStatus.COMPLETA, null, null, null)));
        assertTrue(EmpresaService.sobreviveAProduccion(minuta(MinutaStatus.VALIDADA, null, null, null)));
        assertTrue(EmpresaService.sobreviveAProduccion(minuta(MinutaStatus.BORRADOR, "falta el router", null, null)));
        assertFalse(EmpresaService.sobreviveAProduccion(minuta(MinutaStatus.BORRADOR, null, null, null)));
    }

    @Test
    void sinLogoSaleSoloElNombre() {
        EmpresaService s = con(Empresa.builder().id(1L).nombre("  ").build());
        assertEquals("IDBI", s.nombre());
        assertEquals(null, s.logoDataUri());
    }
}
