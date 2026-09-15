package com.upc.idbi.gateway.chat;

import java.util.List;

public record ChatProposal(
        String summary,
        List<String> asIsFindings,
        List<String> recommendations,
        List<EquipmentRecommendation> equipment,
        String topologyText,
        Topology topology,
        Integer score
) {
}