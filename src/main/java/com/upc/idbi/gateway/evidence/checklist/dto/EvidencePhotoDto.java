package com.upc.idbi.gateway.evidence.checklist.dto;

import java.time.LocalDateTime;

public record EvidencePhotoDto(
        Long id,
        String fileUrl,
        String comment,
        LocalDateTime capturedAt
) {
}
