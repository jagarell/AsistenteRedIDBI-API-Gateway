package com.upc.idbi.gateway.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    private Long evaluationId;
    private String restaurantName;
    private Integer capturedPhotos;
    private Map<String, String> answers;
    /** Marca/modelo detectados por visión IA en fotos de evidencia, por
     * categoría (ej. {"router": {"brand": "TP-Link", "model": "Archer C6"}}). */
    private Map<String, Map<String, String>> detectedEquipment;
}