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
        return m.getVisible() == null || m.getVisible();
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
