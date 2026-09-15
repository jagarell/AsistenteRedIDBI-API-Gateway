package com.upc.idbi.gateway.evidence.checklist;

import com.upc.idbi.gateway.evidence.checklist.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/evaluations/{evaluationId}")
@RequiredArgsConstructor
public class EvidenceChecklistController {

    private final EvidenceChecklistService service;

    @GetMapping("/evidence/checklist")
    public EvidenceChecklistDto getChecklist(@PathVariable Long evaluationId) {
        return service.getChecklist(evaluationId);
    }

    @PostMapping("/evidence/areas")
    public EvidenceAreaItemDto addCustomArea(
            @PathVariable Long evaluationId,
            @RequestBody CreateCustomAreaRequestDto request
    ) {
        return service.addCustomArea(evaluationId, request.name());
    }

    @DeleteMapping("/evidence/areas/{areaId}")
    public void deleteArea(@PathVariable Long evaluationId, @PathVariable Long areaId) {
        service.deleteArea(evaluationId, areaId);
    }

    @PostMapping("/evidence/equipment")
    public EvidenceEquipmentItemDto addCustomEquipment(
            @PathVariable Long evaluationId,
            @RequestBody CreateCustomEquipmentRequestDto request
    ) {
        return service.addCustomEquipment(evaluationId, request.equipmentType(), request.label());
    }

    @DeleteMapping("/evidence/equipment/{equipmentId}")
    public void deleteEquipment(@PathVariable Long evaluationId, @PathVariable Long equipmentId) {
        service.deleteEquipment(evaluationId, equipmentId);
    }

    @PostMapping("/evidence/lock")
    public EvidenceChecklistDto lockSelection(@PathVariable Long evaluationId) {
        return service.lockSelection(evaluationId);
    }

    @PostMapping("/evidence/areas/{areaId}/photos")
    public EvidencePhotoDto uploadAreaPhoto(
            @PathVariable Long evaluationId,
            @PathVariable Long areaId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "comment", required = false) String comment
    ) {
        return service.uploadAreaPhoto(evaluationId, areaId, file, comment);
    }

    @PostMapping("/evidence/equipment/{equipmentId}/photos")
    public EvidencePhotoDto uploadEquipmentPhoto(
            @PathVariable Long evaluationId,
            @PathVariable Long equipmentId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "comment", required = false) String comment
    ) {
        return service.uploadEquipmentPhoto(evaluationId, equipmentId, file, comment);
    }

    @DeleteMapping("/evidence/photos/{photoId}")
    public void deletePhoto(@PathVariable Long evaluationId, @PathVariable Long photoId) {
        service.deletePhoto(evaluationId, photoId);
    }

    @PatchMapping("/evidence/equipment/{equipmentId}")
    public EvidenceEquipmentItemDto updateEquipmentNotes(
            @PathVariable Long evaluationId,
            @PathVariable Long equipmentId,
            @RequestBody UpdateEquipmentNotesRequestDto request
    ) {
        return service.updateEquipmentNotes(evaluationId, equipmentId, request.technicianNotes());
    }

    @GetMapping("/minuta")
    public MinutaDto getMinuta(@PathVariable Long evaluationId) {
        return service.getMinuta(evaluationId);
    }
}
