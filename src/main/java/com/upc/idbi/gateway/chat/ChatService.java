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
    private final com.upc.idbi.gateway.conocimiento.ReglaEmpresaRepository reglas;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public ChatResponse startChat(Long evaluationId, AuthenticatedUser user) {
        ChatStartRequest request = new ChatStartRequest(
                String.valueOf(evaluationId),
                user == null ? null : user.fullName(),
                LocalDate.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                reglas.findById("SISIMP").filter(r -> r.isActiva() && r.getValor() != null && !r.getValor().isBlank())
                        .map(r -> r.getValor().trim()).orElse(null)
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
                List.of(),
                request.mode(),
                null,
                null,
                null
        ));
    }

    public ChatResponse amendChat(Long evaluationId, ChatAmendRequest request) {
        ChatAmendRequest normalized = new ChatAmendRequest(
                String.valueOf(evaluationId), request.state(), request.evidenceCode(),
                request.evidenceScope() == null ? "" : request.evidenceScope(),
                request.fields() == null ? Map.of() : request.fields(),
                request.clarificationKey(), request.clarificationAnswer(),
                request.nodeId(), request.deleted(), request.restore()
        );
        ChatResponse response = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/amend", normalized, ChatResponse.class);

        // La evidencia corregida también se actualiza en la fila guardada.
        if (response != null && response.lastEvidence() != null && request.evidenceCode() != null) {
            updateStoredReading(evaluationId, response.lastEvidence());
        }
        if (response != null && Boolean.TRUE.equals(response.completed())) {
            persistAnswers(evaluationId, response.answers());
        }
        return response;
    }

    /** Editar una respuesta anterior: el motor recalcula todo a partir del estado. */
    public ChatResponse editChat(Long evaluationId, ChatEditRequest request) {
        ChatEditRequest normalized = new ChatEditRequest(
                String.valueOf(evaluationId), request.state(), request.nodeId(),
                request.mode() == null ? "reopen" : request.mode());
        return restTemplate.postForObject(fastApiBaseUrl + "/chat/edit", normalized, ChatResponse.class);
    }

    /** Guarda la lectura confirmada o corregida en las fotos ya guardadas de esa evidencia. */
    private void updateStoredReading(Long evaluationId, Map<String, Object> info) {
        try {
            String json = objectMapper.writeValueAsString(info.get("extracted"));
            String scope = info.get("scope") == null ? "" : String.valueOf(info.get("scope"));
            for (Evidence e : evidenceRepository.findByEvaluationIdAndEvidenceCodeAndChatScope(
                    evaluationId, String.valueOf(info.get("code")), scope)) {
                e.setExtractedJson(json);
                evidenceRepository.save(e);
            }
        } catch (Exception ex) {
            log.warn("No se pudo actualizar la lectura de la evidencia de la evaluación {}", evaluationId, ex);
        }
    }

    /** Variante para nodos EVIDENCE: convierte las fotos subidas a base64 y las
     * manda al mismo endpoint /chat/answer de FastAPI (el motor distingue por
     * el tipo del nodo actual). Si la IA procesó la evidencia, se guardan las
     * fotos como evidencia de la evaluación — para E3 (etiqueta del router)
     * se guardan las versiones con las credenciales desenfocadas, nunca el
     * original. */
    public ChatResponse answerChatWithPhotos(Long evaluationId, List<MultipartFile> files, String state, String mode) {
        return answerChatWithPhotos(evaluationId, files, state, mode, null, null, null);
    }

    public ChatResponse answerChatWithPhotos(Long evaluationId, List<MultipartFile> files, String state, String mode,
                                             String nota, String nodeId) {
        return answerChatWithPhotos(evaluationId, files, state, mode, nota, nodeId, null);
    }

    /** 0.0.7: `mode = "forzar"` + `nota` («Guardar como correcta») y `nodeId` (foto pendiente de «Subir después»). La foto
     * se guarda igual que siempre; la nota opcional viaja en la lectura (`_forzada`, `_nota`) y es lo único que muestra la minuta. */
    public ChatResponse answerChatWithPhotos(Long evaluationId, List<MultipartFile> files, String state, String mode,
                                             String nota, String nodeId, String descripcion) {
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
                String.valueOf(evaluationId), state, "", base64, mode,
                nota == null || nota.isBlank() ? null : nota.trim(),
                nodeId == null || nodeId.isBlank() ? null : nodeId,
                descripcion == null || descripcion.isBlank() ? null : descripcion.trim()
        ));

        if (response != null && response.lastEvidence() != null && response.validationError() == null) {
            storeEvidence(evaluationId, response, originals, files);
        }
        syncEvidenceComments(evaluationId, response);
        return response;
    }

    /** 0.0.15: agregar, editar o borrar (texto vacío) la descripción de una evidencia ya subida. La API la guarda en el
     * estado (sale en la minuta debajo de la foto) y aquí se copia al comentario de las fotos guardadas. */
    public ChatResponse evidenceNote(Long evaluationId, Map<String, Object> body) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("evaluationId", String.valueOf(evaluationId));
        request.put("state", body.get("state"));
        request.put("nodeId", body.get("nodeId"));
        request.put("descripcion", body.get("descripcion") == null ? "" : String.valueOf(body.get("descripcion")));
        ChatResponse response = restTemplate.postForObject(fastApiBaseUrl + "/chat/evidence-note", request, ChatResponse.class);
        if (response != null && response.validationError() == null && body.get("nodeId") != null) {
            String node = String.valueOf(body.get("nodeId"));
            String text = response.evidenceNotes() == null ? null : response.evidenceNotes().get(node);
            setEvidenceComment(evaluationId, node, text);
        }
        if (response != null && Boolean.TRUE.equals(response.completed())) {
            persistAnswers(evaluationId, response.answers());
        }
        return response;
    }

    /** Copia la descripción de la API a las fotos guardadas (columna `comment`). */
    private void syncEvidenceComments(Long evaluationId, ChatResponse response) {
        if (response == null || response.evidenceNotes() == null || response.lastEvidence() == null) {
            return;
        }
        String node = String.valueOf(response.lastEvidence().get("nodeId"));
        setEvidenceComment(evaluationId, node, response.evidenceNotes().get(node));
    }

    private void setEvidenceComment(Long evaluationId, String nodeId, String text) {
        try {
            int i = nodeId.indexOf('#');
            String code = i < 0 ? nodeId : nodeId.substring(0, i);
            String scope = i < 0 ? "" : nodeId.substring(i + 1);
            for (Evidence e : evidenceRepository.findByEvaluationIdAndEvidenceCodeAndChatScope(evaluationId, code, scope)) {
                e.setComment(text == null || text.isBlank() ? null : text);
                evidenceRepository.save(e);
            }
        } catch (Exception ex) {
            log.warn("No se pudo guardar la descripción de la evidencia {} de la evaluación {}", nodeId, evaluationId, ex);
        }
    }

    private static final int ADJUNTO_MAX_FILES = 3;
    private static final long ADJUNTO_MAX_BYTES = 10L * 1024 * 1024;

    /** Lee un documento del cliente con la IA. No es una evidencia de la visita: se guarda como DOC_CLIENTE y
     * no entra en el conteo de evidencias ni en el anexo de fotos. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> leerAdjunto(Long evaluationId, List<MultipartFile> files, String state, String tipo) {
        if (files == null || files.isEmpty() || files.size() > ADJUNTO_MAX_FILES) {
            throw new IllegalArgumentException("Adjunta de 1 a " + ADJUNTO_MAX_FILES + " archivos");
        }
        List<byte[]> originals = new ArrayList<>();
        List<String> base64 = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file.getSize() > ADJUNTO_MAX_BYTES) {
                    throw new IllegalArgumentException("Cada archivo debe pesar hasta 10 MB");
                }
                // PDF: sin conversión en el servidor; se pide una foto del documento.
                if ("application/pdf".equalsIgnoreCase(file.getContentType())) {
                    return Map.of("status", "PDF");
                }
                byte[] bytes = file.getBytes();
                originals.add(bytes);
                base64.add(Base64.getEncoder().encodeToString(bytes));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo subido", ex);
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("evaluationId", String.valueOf(evaluationId));
        body.put("state", state);
        body.put("tipo", tipo == null || tipo.isBlank() ? null : tipo.trim());
        body.put("imagesBase64", base64);
        Map<String, Object> result = restTemplate.postForObject(fastApiBaseUrl + "/chat/adjunto", body, Map.class);
        if (result != null && "OK".equals(result.get("status"))) {
            storeClientDocument(evaluationId, originals, files, String.valueOf(result.get("tipo")));
        }
        return result;
    }

    /** Usa los datos revisados del adjunto y sigue igual que una respuesta normal. */
    public ChatResponse usarAdjunto(Long evaluationId, Map<String, Object> body) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("evaluationId", String.valueOf(evaluationId));
        request.put("state", body.get("state"));
        request.put("campos", body.get("campos") == null ? List.of() : body.get("campos"));
        ChatResponse response = restTemplate.postForObject(fastApiBaseUrl + "/chat/adjunto/usar", request, ChatResponse.class);
        if (response != null && Boolean.TRUE.equals(response.completed())) {
            persistAnswers(evaluationId, response.answers());
        }
        return response;
    }

    private void storeClientDocument(Long evaluationId, List<byte[]> originals, List<MultipartFile> files, String tipo) {
        try {
            for (int i = 0; i < originals.size(); i++) {
                String stored = storageService.saveBytes(evaluationId, originals.get(i), ".jpg");
                evidenceRepository.save(Evidence.builder()
                        .evaluationId(evaluationId)
                        .category("cliente")
                        .storedFileName(stored)
                        .originalFileName(files.get(i).getOriginalFilename())
                        .contentType(files.get(i).getContentType() == null ? "image/jpeg" : files.get(i).getContentType())
                        .uploadedAt(LocalDateTime.now())
                        .evidenceCode("DOC_CLIENTE")
                        .chatScope(tipo)
                        .build());
            }
        } catch (Exception ex) {
            log.warn("No se pudo guardar el documento del cliente de la evaluación {}", evaluationId, ex);
        }
    }

    private ChatResponse callFastApiAndPersist(Long evaluationId, ChatAnswerRequest request) {
        ChatResponse response = restTemplate.postForObject(
                fastApiBaseUrl + "/chat/answer",
                request,
                ChatResponse.class
        );

        // Lectura confirmada con "Correcto" (con o sin ediciones): queda en las fotos guardadas.
        if (response != null && response.lastEvidence() != null
                && Boolean.TRUE.equals(response.lastEvidence().get("confirmed"))) {
            updateStoredReading(evaluationId, response.lastEvidence());
        }
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
                if (("E3".equals(code) || "E2e".equals(code)) && !useProcessed) {
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