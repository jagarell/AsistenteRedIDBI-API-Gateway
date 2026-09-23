package com.upc.idbi.gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;
    private final EvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public ChatResponse startChat(Long evaluationId) {
        ChatStartRequest request = new ChatStartRequest(
                String.valueOf(evaluationId)
        );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/chat/start",
                request,
                ChatResponse.class
        );
    }

    public ChatResponse answerChat(
            Long evaluationId,
            ChatAnswerRequest request
    ) {
        ChatAnswerRequest normalizedRequest =
                new ChatAnswerRequest(
                        String.valueOf(evaluationId),
                        request.currentStep(),
                        request.answer(),
                        request.answers() == null
                                ? new HashMap<>()
                                : request.answers()
                );

        ChatResponse response = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/answer",
                normalizedRequest,
                ChatResponse.class
        );

        if (response != null && Boolean.TRUE.equals(response.completed())) {
            persistAnswers(evaluationId, response.answers());
        }

        return response;
    }

    /** Guarda las respuestas crudas del chat en la evaluación al completarse,
     * para que /analysis y el sembrado del checklist de evidencias puedan
     * funcionar a partir del evaluationId solo (sin que el cliente las
     * reenvíe). También sincroniza el nombre/dirección/tipo reales que el
     * técnico respondió en el chat hacia la evaluación — sin esto, esos
     * campos se quedaban para siempre con el placeholder puesto al crear la
     * evaluación ("Nueva Evaluación"), ya que nada más los actualiza. No es
     * crítico para la respuesta del chat en sí: si falla, se registra y se
     * continúa (el cliente igual recibió su ChatResponse). */
    private void persistAnswers(Long evaluationId, java.util.Map<String, String> answers) {
        try {
            Evaluation evaluation = evaluationRepository.findById(evaluationId).orElse(null);
            if (evaluation == null) {
                return;
            }
            evaluation.setChatAnswersJson(objectMapper.writeValueAsString(answers));

            String establishmentName = answers.get("establishment_name");
            if (establishmentName != null && !establishmentName.isBlank()) {
                evaluation.setRestaurantName(establishmentName.trim());
            }
            String address = answers.get("address");
            if (address != null && !address.isBlank()) {
                evaluation.setAddress(address.trim());
            }
            String establishmentType = answers.get("establishment_type");
            if (establishmentType != null && !establishmentType.isBlank()) {
                evaluation.setEstablishmentType(establishmentType.trim());
            }

            evaluationRepository.save(evaluation);
        } catch (Exception ex) {
            log.warn("No se pudieron persistir las respuestas del chat para la evaluación {}", evaluationId, ex);
        }
    }
}