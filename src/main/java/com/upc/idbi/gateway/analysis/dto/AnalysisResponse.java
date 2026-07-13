package com.upc.idbi.gateway.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    private Integer globalScore;
    private Integer evaluatedAreas;
    private Integer attentionRequired;
    private List<AnalysisItem> results;
    private String summary;
    private List<String> recommendations;
}