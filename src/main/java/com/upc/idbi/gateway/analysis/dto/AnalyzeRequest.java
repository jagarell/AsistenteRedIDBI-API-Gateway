package com.upc.idbi.gateway.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    private Long evaluationId;
    private String restaurantName;
    private Integer capturedPhotos;
}