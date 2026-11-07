package com.upc.idbi.gateway.conocimiento;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Preguntas y reglas (administrador): reglas de la empresa (activar, editar, crear, eliminar) y preguntas agregadas al flujo. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ReglasController {

    private static final Set<String> TIPOS = Set.of("Observación", "Recomendación", "Aviso");

    private final ReglaEmpresaRepository reglas;
    private final PreguntaFlujoRepository preguntas;
    private final com.upc.idbi.gateway.common.FastApiClient fastApi;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.upc.idbi.gateway.bitacora.BitacoraService bitacora;

    /** SISIMP primero y luego por código. */
    @GetMapping("/api/reglas")
    public List<ReglaEmpresa> lista() {
        return reglas.findAll().stream()
                .sorted(Comparator.comparing((ReglaEmpresa r) -> !"SISIMP".equals(r.getId())).thenComparing(ReglaEmpresa::getId))
                .toList();
    }

    @PostMapping("/api/reglas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReglaEmpresa crear(@RequestBody Map<String, Object> b, @AuthenticationPrincipal AuthenticatedUser me) {
        String nombre = texto(b, "nombre");
        if (nombre.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el nombre de la regla.");
        String id = "R_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return reglas.save(ReglaEmpresa.builder().id(id).nombre(nombre).tipo(tipo(b)).activa(true)
                .texto(texto(b, "texto")).cuando(texto(b, "cuando")).origen("administrador")
                .creadaPor(me.fullName()).creadaEn(LocalDateTime.now()).build());
    }

    @PutMapping("/api/reglas/{id}")
    public ReglaEmpresa editar(@PathVariable String id, @RequestBody Map<String, Object> b) {
        ReglaEmpresa r = reglas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa regla."));
        if (b.containsKey("nombre") && !"SISIMP".equals(id) && !texto(b, "nombre").isBlank()) r.setNombre(texto(b, "nombre"));
        if (b.containsKey("tipo") && !"SISIMP".equals(id)) r.setTipo(tipo(b));
        if (b.containsKey("texto")) r.setTexto(texto(b, "texto"));
        if (b.containsKey("cuando") && !"SISIMP".equals(id)) r.setCuando(texto(b, "cuando"));
        if (b.containsKey("activa")) r.setActiva(Boolean.parseBoolean(String.valueOf(b.get("activa"))));
        // SISIMP: «valor» es el nombre del sistema de impresiones de la empresa (por defecto Wrapper).
        if ("SISIMP".equals(id) && b.containsKey("valor")) r.setValor(texto(b, "valor"));
        return reglas.save(r);
    }

    @DeleteMapping("/api/reglas/{id}")
    public Map<String, Object> eliminar(@PathVariable String id) {
        if ("SISIMP".equals(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La regla del sistema de impresiones no se elimina; puedes desactivarla.");
        if (!reglas.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa regla.");
        reglas.deleteById(id);
        return Map.of("id", id, "eliminada", true);
    }

    /** Preguntas que el administrador o el Asistente IA agregaron al flujo (las base viven en el motor del Chat IA). */
    @GetMapping("/api/preguntas")
    public List<PreguntaFlujo> preguntas() {
        return ordenadas();
    }

    private List<PreguntaFlujo> ordenadas() {
        return preguntas.findAll().stream()
                .sorted(Comparator.comparing((PreguntaFlujo p) -> p.getSeccionId() == null ? "" : p.getSeccionId())
                        .thenComparing(p -> p.getOrden() == null ? Integer.MAX_VALUE : p.getOrden()).thenComparing(PreguntaFlujo::getId))
                .toList();
    }

    /**
     * Subir o bajar una pregunta agregada dentro de su sección (doc 0.0.7 §11). {@code dir}: «arriba» o «abajo». Mover dos veces en
     * sentido contrario la deja donde estaba (Deshacer). Las preguntas base viven en el motor del Chat IA y no se reordenan aquí.
     */
    @PutMapping("/api/preguntas/{id}/mover")
    @org.springframework.transaction.annotation.Transactional
    public List<PreguntaFlujo> mover(@PathVariable String id, @RequestBody Map<String, Object> b, @AuthenticationPrincipal AuthenticatedUser me) {
        PreguntaFlujo p = preguntas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa pregunta."));
        List<PreguntaFlujo> seccion = new ArrayList<>(ordenadas().stream().filter(x -> Objects.equals(x.getSeccionId(), p.getSeccionId())).toList());
        for (int i = 0; i < seccion.size(); i++) seccion.get(i).setOrden(i + 1);
        int i = seccion.indexOf(p);
        int j = "arriba".equals(texto(b, "dir")) ? i - 1 : i + 1;
        if (j < 0 || j >= seccion.size()) {
            preguntas.saveAll(seccion);
            return ordenadas();
        }
        Collections.swap(seccion, i, j);
        for (int k = 0; k < seccion.size(); k++) seccion.get(k).setOrden(k + 1);
        preguntas.saveAll(seccion);
        if (bitacora != null) bitacora.registrar(me, null, "empresa", id + " ahora va en la sección " + p.getSeccionId() + ", posición " + (j + 1));
        return ordenadas();
    }

    /** Todas las preguntas del Chat IA por sección, en el orden del motor (solo lectura); el Mapa de nodos usa lo mismo. */
    @GetMapping("/api/flujo/catalogo")
    public Map<String, Object> catalogo() {
        return fastApi.get("/chat/flujo/catalogo");
    }

    private static String texto(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? "" : v.toString().trim();
    }

    private static String tipo(Map<String, Object> b) {
        String t = texto(b, "tipo");
        return TIPOS.contains(t) ? t : "Observación";
    }
}
