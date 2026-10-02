package com.upc.idbi.gateway.map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MapService {

    private static final String STATE_KEY = "__state";

    private final EvaluationRepository evaluationRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    /** El mapa guardado, o `{"map": null}` si todavía no hay. */
    public Map<String, Object> get(Long evaluationId) {
        Evaluation evaluation = require(evaluationId);
        if (evaluation.getMapJson() == null || evaluation.getMapJson().isBlank()) {
            return java.util.Collections.singletonMap("map", null);
        }
        return Map.of("map", read(evaluation.getMapJson()));
    }

    public Map<String, Object> save(Long evaluationId, Map<String, Object> map) {
        Evaluation evaluation = require(evaluationId);
        try {
            evaluation.setMapJson(objectMapper.writeValueAsString(map));
        } catch (IOException e) {
            throw new IllegalArgumentException("Mapa inválido", e);
        }
        evaluationRepository.save(evaluation);
        return map;
    }

    public Map<String, Object> generate(Long evaluationId) {
        Evaluation evaluation = require(evaluationId);
        String state = stateOf(evaluation);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/map/generate", Map.of("state", state), Map.class);
        if (map == null) {
            throw new IllegalStateException("El motor no devolvió el mapa");
        }
        return save(evaluationId, map);
    }

    public Map<String, Object> command(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/map/command", body, Map.class);
        return result;
    }

    private Evaluation require(Long id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación " + id));
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Mapa guardado inválido", e);
        }
    }

    private String stateOf(Evaluation evaluation) {
        try {
            if (evaluation.getChatAnswersJson() == null) {
                throw new IllegalArgumentException("El chat de esta evaluación aún no se completó");
            }
            Map<String, String> answers = objectMapper.readValue(
                    evaluation.getChatAnswersJson(), new TypeReference<Map<String, String>>() {});
            String state = answers.get(STATE_KEY);
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("Esta evaluación no tiene datos para generar el mapa");
            }
            return state;
        } catch (IOException e) {
            throw new IllegalStateException("Respuestas del chat inválidas", e);
        }
    }
}
