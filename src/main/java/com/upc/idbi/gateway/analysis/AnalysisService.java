package com.upc.idbi.gateway.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.analysis.dto.AnalysisResponse;
import com.upc.idbi.gateway.analysis.dto.AnalyzeRequest;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final RestTemplate restTemplate;
    private final EvaluationRepository evaluationRepository;
    private final EvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public AnalysisResponse analyze(Long evaluationId, Map<String, String> answers) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la evaluación con id " + evaluationId
                ));

        // Se envían los datos reales de la evaluación y las respuestas del
        // chat técnico, para que el análisis IA refleje el levantamiento real
        // en vez de un payload ficticio. Si el cliente no las mandó (ej. la
        // pantalla de propuesta las pide sin volver a pasarlas), se cae a las
        // respuestas persistidas al completar el chat — nunca a un mapa
        // vacío, que antes pisaba en silencio el análisis real ya calculado.
        Map<String, String> resolvedAnswers = (answers != null && !answers.isEmpty())
                ? answers
                : loadPersistedAnswers(evaluation);

        AnalyzeRequest request = new AnalyzeRequest(
                evaluation.getId(),
                evaluation.getRestaurantName(),
                evaluation.getProgress(),
                resolvedAnswers,
                buildDetectedEquipment(evaluationId)
        );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/analyze",
                request,
                AnalysisResponse.class
        );
    }

    /** Marca/modelo detectados por visión IA en las fotos de evidencia ya
     * subidas, para que el motor de propuesta pueda contrastarlos contra lo
     * autorreportado en el chat. Una entrada por categoría (la foto más
     * reciente con dato legible gana). */
    private Map<String, Map<String, String>> buildDetectedEquipment(Long evaluationId) {
        Map<String, Map<String, String>> detected = new LinkedHashMap<>();
        for (Evidence evidence : evidenceRepository.findByEvaluationIdOrderByUploadedAtDesc(evaluationId)) {
            if (detected.containsKey(evidence.getCategory())) {
                continue;
            }
            String brand = evidence.getDetectedBrand();
            String model = evidence.getDetectedModel();
            if ((brand == null || brand.isBlank()) && (model == null || model.isBlank())) {
                continue;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            if (brand != null && !brand.isBlank()) {
                entry.put("brand", brand);
            }
            if (model != null && !model.isBlank()) {
                entry.put("model", model);
            }
            detected.put(evidence.getCategory(), entry);
        }
        return detected;
    }

    private Map<String, String> loadPersistedAnswers(Evaluation evaluation) {
        String json = evaluation.getChatAnswersJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            log.warn("No se pudieron leer las respuestas persistidas de la evaluación {}", evaluation.getId(), ex);
            return Collections.emptyMap();
        }
    }
}
