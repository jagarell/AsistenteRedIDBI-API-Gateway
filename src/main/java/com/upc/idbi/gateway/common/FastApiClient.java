package com.upc.idbi.gateway.common;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Llamadas al servicio de IA (FastAPI) que solo calcula: plan de cambios, minuta de soporte, aprendizaje y fichas.
 * Lo que se guarda (minutas, versiones, reglas, preguntas, conocimiento) queda en este gateway.
 */
@Component
@RequiredArgsConstructor
public class FastApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestTemplate restTemplate;

    @Value("${app.fastapi.base-url}")
    private String baseUrl;

    public Map<String, Object> post(String path, Object body) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body), MAP).getBody();
    }
}
