package com.upc.idbi.gateway.asistente;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qué frases abren «Nueva minuta» en el Asistente IA (el resto lo contesta ChatGPT o las otras tarjetas). */
class IntencionNuevaMinutaTest {

    private static boolean abre(String t) {
        return AsistenteChatService.NUEVA.matcher(t).find();
    }

    @Test
    void frasesQueAbrenUnaMinutaNueva() {
        assertTrue(abre("Quiero una minuta de soporte"));
        assertTrue(abre("necesito una minuta de implementación"));
        assertTrue(abre("Nueva minuta"));
        assertTrue(abre("Redacta una minuta de la visita"));
        assertTrue(abre("otra minuta de soporte para el mismo local"));
    }

    @Test
    void frasesQueNoLaAbren() {
        assertFalse(abre("¿Cuántas minutas de soporte hay este mes?"));
        assertFalse(abre("Muéstrame la minuta de Restobar Larco"));
        assertFalse(abre("Reportes"));
    }
}
