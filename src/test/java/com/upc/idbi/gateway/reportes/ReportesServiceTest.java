package com.upc.idbi.gateway.reportes;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportesServiceTest {

    private static ReportesService.Fila fila(String tec, String estado, Integer pct, String plazo, Integer min, String fecha) {
        return new ReportesService.Fila(1L, LocalDate.parse(fecha).atTime(10, 0), 1L, tec, "Sup", "Local", "Cafetería", estado,
                ReportesService.criticidadDe(pct), pct, "Básica", plazo, min, "IMPLEMENTACION", null);
    }

    private static final ReportesService.Filtros SIN = new ReportesService.Filtros(null, null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    @Test
    void indicadoresSeCalculanConLasFilasFiltradas() {
        List<ReportesService.Fila> L = List.of(fila("Ana", "Validada", 90, "A tiempo", 60, "2026-10-01"), fila("Ana", "Pendiente", 50, "En mora", null, "2026-10-02"),
                fila("Luis", "Completa", 70, "A tiempo", 120, "2026-10-03"));
        Map<String, Object> k = ReportesService.indicadores(L);
        assertThat(k).containsEntry("completadas", 2).containsEntry("total", 3).containsEntry("aTiempoPct", 67L).containsEntry("tiempoPromedioMin", 90L).containsEntry("enRiesgo", 1L);
        ReportesService.Filtros soloValidadas = new ReportesService.Filtros(null, null, Set.of(), Set.of(), Set.of(), Set.of("Validada"), Set.of(), Set.of(), Set.of());
        assertThat(L.stream().filter(f -> ReportesService.pasa(f, soloValidadas, null)).count()).isEqualTo(1);
        ReportesService.Filtros rango = new ReportesService.Filtros(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        assertThat(L.stream().filter(f -> ReportesService.pasa(f, rango, null)).count()).isEqualTo(2);
        assertThat(L.stream().filter(f -> ReportesService.pasa(f, SIN, null)).count()).isEqualTo(3);
    }

    @Test
    void estadoCriticidadYPlazo() {
        Minuta m = Minuta.builder().status(MinutaStatus.BORRADOR).rejectionReason("falta selftest").createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
        assertThat(ReportesService.estadoDe(m)).isEqualTo("En revisión");
        assertThat(ReportesService.criticidadDe(null)).isEqualTo("Sin análisis");
        assertThat(ReportesService.criticidadDe(59)).isEqualTo("Riesgo");
        assertThat(ReportesService.plazoDe(m, LocalDateTime.of(2026, 10, 1, 18, 0))).isEqualTo("A tiempo");
        assertThat(ReportesService.plazoDe(m, LocalDateTime.of(2026, 10, 3, 9, 0))).isEqualTo("En mora");
        m.setEsfuerzo("Compleja");
        assertThat(ReportesService.plazoDe(m, LocalDateTime.of(2026, 10, 3, 9, 0))).isEqualTo("A tiempo");
    }
}
