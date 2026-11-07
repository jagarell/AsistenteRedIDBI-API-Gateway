package com.upc.idbi.gateway.empresa;

import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Empresa y ambiente (doc 0.0.7 §16 y §31). En modo demo todo cuenta. Al pasar a producción las minutas que existían quedan como
 * «etapa demo»: se conservan las Completas, En revisión y Validadas (visibles, pero no cuentan) y dejan de verse las demás.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    public static final String NOMBRE_POR_DEFECTO = "IDBI";

    private final EmpresaRepository repo;

    public synchronized Empresa get() {
        return repo.findById(1L).orElseGet(() -> repo.save(Empresa.builder().id(1L).nombre(NOMBRE_POR_DEFECTO).copiaFrecuencia("off").copiaHora(2).copiaIncluyeFotos(false).build()));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    public static final double COSTO_HORA_POR_DEFECTO = 25.0;

    /** Plazos de la empresa: minutos manuales por rubro (antes de la app), días por nivel de esfuerzo y costo por hora. */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> plazos() {
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
        try {
            String j = get().getPlazosJson();
            if (j != null && !j.isBlank()) {
                p.putAll(JSON.readValue(j, java.util.Map.class));
            }
        } catch (Exception ignored) {
            // se usan los valores por defecto
        }
        p.putIfAbsent("rubros", new java.util.LinkedHashMap<String, Object>(java.util.Map.of("Restaurante", 180, "Fast food", 150, "Retail", 120, "Otro", 150)));
        p.putIfAbsent("costoHora", COSTO_HORA_POR_DEFECTO);
        return p;
    }

    public double costoHora() {
        Object c = plazos().get("costoHora");
        return c instanceof Number n && n.doubleValue() > 0 ? n.doubleValue() : COSTO_HORA_POR_DEFECTO;
    }

    public synchronized void guardarPlazos(java.util.Map<String, Object> p) {
        try {
            Empresa e = get();
            e.setPlazosJson(JSON.writeValueAsString(p));
            repo.save(e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Plazos no válidos");
        }
    }

    public boolean enProduccion() {
        return get().getProduccionDesde() != null;
    }

    public String nombre() {
        String n = get().getNombre();
        return n == null || n.isBlank() ? NOMBRE_POR_DEFECTO : n;
    }

    /** Logo como data URI, o null si no hay. */
    public String logoDataUri() {
        Empresa e = get();
        return e.getLogoBase64() == null || e.getLogoBase64().isBlank() ? null : "data:" + (e.getLogoMime() == null ? "image/png" : e.getLogoMime()) + ";base64," + e.getLogoBase64();
    }

    /** ¿Cuenta para reportes, indicadores y panel? En producción, solo las minutas de la etapa de producción. */
    public boolean cuenta(Minuta m) {
        return !enProduccion() || !"demo".equals(m.getEtapa());
    }

    /** ¿Se ve? Las minutas demo Borrador o Pendiente dejan de verse al pasar a producción. */
    public boolean visible(Minuta m) {
        return !m.archivada() && (m.getVisible() == null || m.getVisible());
    }

    public List<Minuta> contadas(List<Minuta> todas) {
        return todas.stream().filter(m -> visible(m) && cuenta(m)).toList();
    }

    /** Estado final de visibilidad de una minuta demo al pasar a producción. */
    public static boolean sobreviveAProduccion(Minuta m) {
        return m.getStatus() == MinutaStatus.COMPLETA || m.getStatus() == MinutaStatus.VALIDADA
                || (m.getStatus() == MinutaStatus.BORRADOR && m.getRejectionReason() != null && !m.getRejectionReason().isBlank());
    }
}
