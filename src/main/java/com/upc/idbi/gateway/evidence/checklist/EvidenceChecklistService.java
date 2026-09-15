package com.upc.idbi.gateway.evidence.checklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.evaluation.Evaluation;
import com.upc.idbi.gateway.evaluation.EvaluationRepository;
import com.upc.idbi.gateway.evidence.Evidence;
import com.upc.idbi.gateway.evidence.EvidencePhotoAnalysisService;
import com.upc.idbi.gateway.evidence.EvidenceRepository;
import com.upc.idbi.gateway.evidence.EvidenceStorageService;
import com.upc.idbi.gateway.evidence.checklist.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checklist dinámico de evidencias (áreas + equipos a fotografiar), sembrado
 * desde las respuestas reales del chat técnico (ver
 * app.chat.checklist.build_evidence_checklist en idbi-fastapi) y editable en
 * Fase A (selectionLocked=false); al bloquear (Fase B) cada ítem necesita al
 * menos una foto antes de habilitar el análisis IA. Un ítem sembrado que no
 * aplica en el local real (ej. "Rack / Router" si no existe) también se
 * puede quitar en Fase B, pero solo mientras siga sin fotos — ver
 * ensureAreaDeletable/ensureEquipmentDeletable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceChecklistService {

    private final EvidenceAreaRepository areaRepository;
    private final EvidenceEquipmentItemRepository equipmentRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageService storageService;
    private final EvidencePhotoAnalysisService analysisService;
    private final EvaluationRepository evaluationRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.fastapi.base-url}")
    private String fastApiBaseUrl;

    public EvidenceChecklistDto getChecklist(Long evaluationId) {
        ensureSeeded(evaluationId);
        return buildChecklistDto(evaluationId);
    }

    public EvidenceAreaItemDto addCustomArea(Long evaluationId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del área no puede estar vacío.");
        }
        ensureNotLocked(evaluationId);
        EvidenceArea area = areaRepository.save(
                EvidenceArea.builder().evaluationId(evaluationId).name(name.trim()).custom(true).build()
        );
        return toAreaDto(area);
    }

    public void deleteArea(Long evaluationId, Long areaId) {
        ensureAreaDeletable(evaluationId, areaId);
        areaRepository.deleteById(areaId);
    }

    public EvidenceEquipmentItemDto addCustomEquipment(Long evaluationId, String equipmentType, String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("La etiqueta del equipo no puede estar vacía.");
        }
        ensureNotLocked(evaluationId);
        EvidenceEquipmentItem item = equipmentRepository.save(
                EvidenceEquipmentItem.builder()
                        .evaluationId(evaluationId)
                        .equipmentType(equipmentType == null || equipmentType.isBlank() ? "otro" : equipmentType)
                        .label(label.trim())
                        .custom(true)
                        .build()
        );
        return toEquipmentDto(item);
    }

    public void deleteEquipment(Long evaluationId, Long equipmentId) {
        ensureEquipmentDeletable(evaluationId, equipmentId);
        equipmentRepository.deleteById(equipmentId);
    }

    public EvidenceChecklistDto lockSelection(Long evaluationId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        evaluation.setEvidenceSelectionLocked(true);
        evaluationRepository.save(evaluation);
        return buildChecklistDto(evaluationId);
    }

    public EvidencePhotoDto uploadAreaPhoto(Long evaluationId, Long areaId, MultipartFile file, String comment) {
        areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el área " + areaId));

        String storedFileName = storageService.save(evaluationId, file);
        Evidence evidence = Evidence.builder()
                .evaluationId(evaluationId)
                .category("area")
                .storedFileName(storedFileName)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .uploadedAt(LocalDateTime.now())
                .comment(comment)
                .areaId(areaId)
                .build();

        return toPhotoDto(evidenceRepository.save(evidence));
    }

    public EvidencePhotoDto uploadEquipmentPhoto(Long evaluationId, Long equipmentId, MultipartFile file, String comment) {
        EvidenceEquipmentItem item = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el equipo " + equipmentId));

        String storedFileName = storageService.save(evaluationId, file);
        Evidence evidence = Evidence.builder()
                .evaluationId(evaluationId)
                .category(item.getEquipmentType())
                .storedFileName(storedFileName)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .uploadedAt(LocalDateTime.now())
                .comment(comment)
                .equipmentItemId(equipmentId)
                .build();

        // Analiza la foto en el momento (mismo motor de visión que el flujo
        // simple de 7 categorías) — si detecta marca/modelo, los guarda en
        // el ítem para que el motor de propuesta los pueda contrastar contra
        // lo autorreportado en el chat (ver AnalysisService.buildDetectedEquipment).
        byte[] imageBytes = storageService.read(evaluationId, storedFileName);
        var result = analysisService.analyze(item.getEquipmentType(), imageBytes);
        evidence.setAnalysisResult(result.description());
        evidence.setDetectedBrand(result.brand());
        evidence.setDetectedModel(result.model());
        Evidence saved = evidenceRepository.save(evidence);

        if ((result.brand() != null && !result.brand().isBlank())
                || (result.model() != null && !result.model().isBlank())) {
            Map<String, Object> specs = new LinkedHashMap<>();
            if (result.brand() != null && !result.brand().isBlank()) {
                specs.put("brand", result.brand());
            }
            if (result.model() != null && !result.model().isBlank()) {
                specs.put("model", result.model());
            }
            try {
                item.setExtractedSpecsJson(objectMapper.writeValueAsString(specs));
                equipmentRepository.save(item);
            } catch (Exception ex) {
                log.warn("No se pudieron guardar los specs detectados del equipo {}", equipmentId, ex);
            }
        }

        return toPhotoDto(saved);
    }

    public void deletePhoto(Long evaluationId, Long photoId) {
        Evidence evidence = evidenceRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la foto " + photoId));
        storageService.delete(evaluationId, evidence.getStoredFileName());
        evidenceRepository.deleteById(photoId);
    }

    public EvidenceEquipmentItemDto updateEquipmentNotes(Long evaluationId, Long equipmentId, String notes) {
        EvidenceEquipmentItem item = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el equipo " + equipmentId));
        item.setTechnicianNotes(notes);
        equipmentRepository.save(item);
        return toEquipmentDto(item);
    }

    public MinutaDto getMinuta(Long evaluationId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        ensureSeeded(evaluationId);

        Map<String, String> answers = loadAnswers(evaluation);
        List<ChatAnswerDto> conversation = buildConversation(answers);
        List<EvidenceAreaItemDto> areas = areaRepository.findByEvaluationId(evaluationId).stream()
                .map(this::toAreaDto).toList();
        List<EvidenceEquipmentItemDto> equipment = equipmentRepository.findByEvaluationId(evaluationId).stream()
                .map(this::toEquipmentDto).toList();
        List<EquipmentTableRowDto> table = equipment.stream()
                .map(e -> new EquipmentTableRowDto(
                        e.label(), e.equipmentType(), e.extractedSpecs(), e.technicianNotes(), e.photos().size()
                ))
                .toList();

        return new MinutaDto(
                evaluationId,
                evaluation.getRestaurantName(),
                evaluation.getAddress(),
                conversation,
                areas,
                equipment,
                table
        );
    }

    // --- Internos ---

    private void ensureSeeded(Long evaluationId) {
        if (areaRepository.existsByEvaluationId(evaluationId)) {
            return;
        }
        Evaluation evaluation = requireEvaluation(evaluationId);
        Map<String, String> answers = loadAnswers(evaluation);
        if (answers.isEmpty()) {
            // El chat todavía no se completó (o no dejó respuestas) — no hay
            // nada de qué sembrar todavía; el checklist queda vacío hasta
            // que el técnico agregue ítems a mano en Fase A.
            return;
        }

        try {
            ChecklistSeedResponse seed = restTemplate.postForObject(
                    fastApiBaseUrl + "/evidence-checklist/seed",
                    new ChecklistSeedRequest(answers),
                    ChecklistSeedResponse.class
            );
            if (seed == null) {
                return;
            }
            for (ChecklistSeedResponse.AreaSeed area : seed.areas()) {
                areaRepository.save(
                        EvidenceArea.builder().evaluationId(evaluationId).name(area.name()).custom(false).build()
                );
            }
            for (ChecklistSeedResponse.EquipmentSeed item : seed.equipment()) {
                equipmentRepository.save(
                        EvidenceEquipmentItem.builder()
                                .evaluationId(evaluationId)
                                .equipmentType(item.equipmentType())
                                .label(item.label())
                                .custom(false)
                                .build()
                );
            }
        } catch (Exception ex) {
            log.warn("No se pudo sembrar el checklist de evidencias para la evaluación {}", evaluationId, ex);
        }
    }

    private List<ChatAnswerDto> buildConversation(Map<String, String> answers) {
        if (answers.isEmpty()) {
            return List.of();
        }
        try {
            ChatNodeDto[] nodes = restTemplate.getForObject(fastApiBaseUrl + "/chat/nodes", ChatNodeDto[].class);
            if (nodes == null) {
                return List.of();
            }
            List<ChatAnswerDto> result = new ArrayList<>();
            for (ChatNodeDto node : nodes) {
                String answer = answers.get(node.key());
                if (answer != null && !answer.isBlank()) {
                    result.add(new ChatAnswerDto(node.key(), node.question(), answer));
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("No se pudo obtener la metadata de preguntas del chat", ex);
            return List.of();
        }
    }

    private EvidenceChecklistDto buildChecklistDto(Long evaluationId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        List<EvidenceAreaItemDto> areas = areaRepository.findByEvaluationId(evaluationId).stream()
                .map(this::toAreaDto).toList();
        List<EvidenceEquipmentItemDto> equipment = equipmentRepository.findByEvaluationId(evaluationId).stream()
                .map(this::toEquipmentDto).toList();

        boolean allItemsHavePhoto = !areas.isEmpty() || !equipment.isEmpty();
        allItemsHavePhoto = allItemsHavePhoto
                && areas.stream().allMatch(a -> !a.photos().isEmpty())
                && equipment.stream().allMatch(e -> !e.photos().isEmpty());

        return new EvidenceChecklistDto(
                evaluationId,
                Boolean.TRUE.equals(evaluation.getEvidenceSelectionLocked()),
                areas,
                equipment,
                allItemsHavePhoto
        );
    }

    private EvidenceAreaItemDto toAreaDto(EvidenceArea area) {
        List<EvidencePhotoDto> photos = evidenceRepository.findByAreaIdOrderByUploadedAtDesc(area.getId())
                .stream().map(this::toPhotoDto).toList();
        return new EvidenceAreaItemDto(area.getId(), area.getName(), area.isCustom(), photos);
    }

    private EvidenceEquipmentItemDto toEquipmentDto(EvidenceEquipmentItem item) {
        List<EvidencePhotoDto> photos = evidenceRepository.findByEquipmentItemIdOrderByUploadedAtDesc(item.getId())
                .stream().map(this::toPhotoDto).toList();
        return new EvidenceEquipmentItemDto(
                item.getId(),
                item.getEquipmentType(),
                item.getLabel(),
                item.isCustom(),
                parseSpecs(item.getExtractedSpecsJson()),
                item.getTechnicianNotes(),
                photos
        );
    }

    private EvidencePhotoDto toPhotoDto(Evidence evidence) {
        return new EvidencePhotoDto(
                evidence.getId(),
                storageService.urlFor(evidence.getEvaluationId(), evidence.getStoredFileName()),
                evidence.getComment(),
                evidence.getUploadedAt()
        );
    }

    private Map<String, Object> parseSpecs(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, String> loadAnswers(Evaluation evaluation) {
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

    private void ensureNotLocked(Long evaluationId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        if (Boolean.TRUE.equals(evaluation.getEvidenceSelectionLocked())) {
            throw new IllegalArgumentException(
                    "La selección de evidencias ya está confirmada; no se pueden agregar ítems."
            );
        }
    }

    /**
     * En Fase A (sin bloquear) siempre se puede quitar. Ya bloqueada (Fase
     * B), solo si el ítem sigue sin ninguna foto — sirve para descartar
     * ítems sembrados automáticamente que no aplican en el local real (ej.
     * "Rack / Router" o una zona del chat que finalmente no tiene equipo),
     * sin arriesgar borrar evidencia ya capturada.
     */
    private void ensureAreaDeletable(Long evaluationId, Long areaId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        if (!Boolean.TRUE.equals(evaluation.getEvidenceSelectionLocked())) {
            return;
        }
        if (evidenceRepository.existsByAreaId(areaId)) {
            throw new IllegalArgumentException(
                    "No se puede quitar un área que ya tiene fotos."
            );
        }
    }

    private void ensureEquipmentDeletable(Long evaluationId, Long equipmentId) {
        Evaluation evaluation = requireEvaluation(evaluationId);
        if (!Boolean.TRUE.equals(evaluation.getEvidenceSelectionLocked())) {
            return;
        }
        if (evidenceRepository.existsByEquipmentItemId(equipmentId)) {
            throw new IllegalArgumentException(
                    "No se puede quitar un equipo que ya tiene fotos."
            );
        }
    }

    private Evaluation requireEvaluation(Long evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la evaluación con id " + evaluationId));
    }
}
