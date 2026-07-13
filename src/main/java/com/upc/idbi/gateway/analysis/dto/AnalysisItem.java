package com.upc.idbi.gateway.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisItem {

    private String title;
    private String status;
    private Integer score;
    private String description;
    private String color;
}