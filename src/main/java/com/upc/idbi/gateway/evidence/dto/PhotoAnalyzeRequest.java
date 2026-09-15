package com.upc.idbi.gateway.evidence.dto;

public record PhotoAnalyzeRequest(
        String category,
        String imageBase64
) {
}
