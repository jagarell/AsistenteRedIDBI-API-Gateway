package com.upc.idbi.gateway.evidence.checklist.dto;

import java.util.List;

public record EvidenceAreaItemDto(
        Long id,
        String name,
        boolean isCustom,
        List<EvidencePhotoDto> photos
) {
}
