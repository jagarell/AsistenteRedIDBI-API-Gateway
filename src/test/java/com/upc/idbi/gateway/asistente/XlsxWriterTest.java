package com.upc.idbi.gateway.asistente;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxWriterTest {

    @Test
    void generaUnLibroConTituloEncabezadosFiltroYPuntajeNumerico() throws Exception {
        List<List<Object>> filas = new ArrayList<>();
        filas.add(new ArrayList<>(Arrays.asList("Restobar & Larco", "Tec Prueba", "Lucia Torres", "Completa", 72, "06/11/2026", "Av. Larco 123")));
        byte[] xlsx = XlsxWriter.hoja("Reporte de minutas", "Generado el 06/11/2026", List.of("Local", "Técnico", "Supervisor", "Estado", "Puntaje", "Fecha", "Dirección"), filas);
        Map<String, String> partes = new java.util.HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                partes.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertTrue(partes.containsKey("[Content_Types].xml") && partes.containsKey("xl/workbook.xml") && partes.containsKey("xl/styles.xml"));
        String hoja = partes.get("xl/worksheets/sheet1.xml");
        assertTrue(hoja.contains("Reporte de minutas") && hoja.contains("Generado el"));
        assertTrue(hoja.contains("Restobar &amp; Larco"), "el texto se escapa");
        assertTrue(hoja.contains("<c r=\"E5\"><v>72</v></c>"), "el puntaje es un número, no texto");
        assertTrue(hoja.contains("<autoFilter ref=\"A4:G5\"/>"));
        assertEquals(1, hoja.split("<autoFilter").length - 1);
    }
}
