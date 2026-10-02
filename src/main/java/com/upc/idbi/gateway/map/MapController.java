package com.upc.idbi.gateway.map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mapa de red editable de una evaluación. El documento (nodos, enlaces, cajas
 * de texto, fotos) es un JSON que define FastAPI (app/chat/map_model.py); el
 * gateway lo guarda tal cual y lo reenvía a la minuta.
 */
@RestController
@RequestMapping("/api/evaluations/{evaluationId}/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    @GetMapping
    public Map<String, Object> get(@PathVariable Long evaluationId) {
        return mapService.get(evaluationId);
    }

    @PutMapping
    public Map<String, Object> save(@PathVariable Long evaluationId, @RequestBody Map<String, Object> map) {
        return mapService.save(evaluationId, map);
    }

    /** "Generar mapa con IA": arma el mapa a partir de lo levantado en el chat y lo guarda. */
    @PostMapping("/generate")
    public Map<String, Object> generate(@PathVariable Long evaluationId) {
        return mapService.generate(evaluationId);
    }

    /** "Pide un cambio al mapa…": aplica una orden en lenguaje natural (no guarda). */
    @PostMapping("/command")
    public Map<String, Object> command(@PathVariable Long evaluationId, @RequestBody Map<String, Object> body) {
        return mapService.command(body);
    }
}
