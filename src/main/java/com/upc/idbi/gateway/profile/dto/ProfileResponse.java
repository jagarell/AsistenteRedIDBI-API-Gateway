package com.upc.idbi.gateway.profile.dto;

public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String company,
        String city,
        String role,
        Integer totalEvaluations,
        Integer totalProposals,
        Integer sentProposals
) {
}
