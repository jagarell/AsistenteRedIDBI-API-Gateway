package com.upc.idbi.gateway.geo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Ubicación GPS del local (docs/0.0.7/03-gps-direccion.md): la app manda las coordenadas y el motor devuelve calle, distrito,
 * departamento, coincidencia con el nombre del local y el enlace de Google Maps. La clave del servicio de mapas queda en el servidor.
 */
@RestController
@RequiredArgsConstructor
public class GeoController {

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    @PostMapping("/api/geo/reverse")
    @SuppressWarnings("unchecked")
    public Map<String, Object> reverse(@RequestBody Map<String, Object> req) {
        Map<String, Object> body = new HashMap<>();
        body.put("lat", req.get("lat"));
        body.put("lng", req.get("lng"));
        body.put("precision_m", req.get("precision_m"));
        body.put("nombre_local", req.get("nombre_local"));
        Map<String, Object> out = restTemplate.postForObject(fastApiBaseUrl + "/geo/reverse", body, Map.class);
        return out != null ? out : new HashMap<>();
    }
}
