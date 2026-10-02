package com.upc.idbi.gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidenceRepository;
import com.upc.idbi.gateway.evidence.EvidenceStorageService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;
    private final EvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageService storageService;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public ChatResponse startChat(Long evaluationId, AuthenticatedUser user) {
        ChatStartRequest request = new ChatStartRequest(
                String.valueOf(evaluationId),
                user == null ? null : user.fullName(),
                LocalDate.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );

        return restTemplate.postForObject(
                fastApiBaseUrl + "/chat/start",
                request,
                ChatResponse.class
        );
    }

    public ChatResponse answerChat(Long evaluationId, ChatAnswerRequest request) {
        return callFastApiAndPersist(evaluationId, new ChatAnswerRequest(
                String.valueOf(evaluationId),
                request.state(),
                request.answer() == null ? "" : request.answer(),
                List.of()
        ));
    }

    public ChatResponse amendChat(Long evaluationId, ChatAmendRequest request) {
        ChatAmendRequest normalized = new ChatAmendRequest(
                String.valueOf(evaluationId), request.state(), request.evidenceCode(),
                request.evidenceScope() == null ? "" : request.evidenceScope(),
                request.fields() == null ? Map.of() : request.fields(),
                request.clarificationKey(), request.clarificationAnswer()
        );
        ChatResponse response = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/amend", normalized, ChatResponse.class);

        // La evidencia corregida también se actualiza en la fila guardada.
        if (response != null && response.lastEvidence() != null && request.evidenceCode() != null) {
            try {
                String json = objectMapper.writeValueAsString(response.lastEvidence().get("extracted"));
                for (Evidence e : evidenceRepository.findByEvaluationIdAndEvidenceCodeAndChatScope(
                        evaluationId, request.evidenceCode(), normalized.evidenceScope())) {
                    e.setExtractedJson(json);
                    evidenceRepository.save(e);
                }
            } catch (Exception ex) {
                log.warn("No se pudo actualizar la evidencia corregida de la evaluación {}", evaluationId, ex);
            }
        }
        return response;
    }

    /** Variante para nodos EVIDENCE: convierte las fotos subidas a base64 y las
     * manda al mismo endpoint /chat/answer de FastAPI (el motor distingue por
     * el tipo del nodo actual). Si la IA procesó la evidencia, se guardan las
     * fotos como evidencia de la evaluación — para E3 (etiqueta del router)
     * se guardan las versiones con las credenciales desenfocadas, nunca el
     * original. */
    public ChatResponse answerChatWithPhotos(Long evaluationId, List<MultipartFile> files, String state) {
        List<byte[]> originals = new ArrayList<>();
        List<String> base64 = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                byte[] bytes = file.getBytes();
                originals.add(bytes);
                base64.add(Base64.getEncoder().encodeToString(bytes));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo subido", ex);
        }

        ChatResponse response = callFastApiAndPersist(evaluationId, new ChatAnswerRequest(
                String.valueOf(evaluationId), state, "", base64
        ));

        if (response != null && response.lastEvidence() != null && response.validationError() == null) {
            storeEvidence(evaluationId, response, originals, files);
        }
        return response;
    }

    private ChatResponse callFastApiAndPersist(Long evaluationId, ChatAnswerRequest request) {
        ChatResponse response = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/answer",
                request,
                ChatResponse.class
        );

        if (response != null && Boolean.TRUE.equals(response.completed())) {
            persistAnswers(evaluationId, response.answers());
        }

        return response;
    }

    /** Guarda las fotos de una evidencia del chat. Si el usuario volvió atrás y
     * la sube de nuevo, reemplaza la anterior del mismo código y alcance. */
    private void storeEvidence(Long evaluationId, ChatResponse response,
                               List<byte[]> originals, List<MultipartFile> files) {
        try {
            Map<String, Object> info = response.lastEvidence();
            String code = String.valueOf(info.get("code"));
            String scope = info.get("scope") == null ? "" : String.valueOf(info.get("scope"));

            for (Evidence previous : evidenceRepository
                    .findByEvaluationIdAndEvidenceCodeAndChatScope(evaluationId, code, scope)) {
                storageService.delete(evaluationId, previous.getStoredFileName());
                evidenceRepository.delete(previous);
            }

            List<String> processed = response.processedImages();
            String extractedJson = objectMapper.writeValueAsString(info.get("extracted"));
            for (int i = 0; i < originals.size(); i++) {
                boolean useProcessed = processed != null && i < processed.size() && !processed.get(i).isBlank();
                byte[] content = useProcessed ? Base64.getDecoder().decode(processed.get(i)) : originals.get(i);
                // Si se pidió ocultar credenciales (E3) y no hay versión procesada, no se guarda nada.
                if ("E3".equals(code) && !useProcessed) {
                    continue;
                }
                String stored = storageService.saveBytes(evaluationId, content, ".jpg");
                evidenceRepository.save(Evidence.builder()
                        .evaluationId(evaluationId)
                        .category("chat")
                        .storedFileName(stored)
                        .originalFileName(files.get(i).getOriginalFilename())
                        .contentType("image/jpeg")
                        .uploadedAt(LocalDateTime.now())
                        .evidenceCode(code)
                        .chatScope(scope)
                        .chatArea(info.get("area") == null ? null : String.valueOf(info.get("area")))
                        .chatEquipo(info.get("equipo") == null ? null : String.valueOf(info.get("equipo")))
                        .extractedJson(extractedJson)
                        .build());
            }
        } catch (Exception ex) {
            log.warn("No se pudo guardar la evidencia del chat para la evaluación {}", evaluationId, ex);
        }
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

            String contact = answers.get("visita.contacto");
            if (contact != null && !contact.isBlank()) {
                evaluation.setContactName(contact.trim());
            }
            String phone = answers.get("visita.telefono");
            if (phone != null && !phone.isBlank()) {
                evaluation.setPhone(phone.trim());
            }

            evaluationRepository.save(evaluation);
        } catch (Exception ex) {
            log.warn("No se pudieron persistir las respuestas del chat para la evaluación {}", evaluationId, ex);
        }
    }
}