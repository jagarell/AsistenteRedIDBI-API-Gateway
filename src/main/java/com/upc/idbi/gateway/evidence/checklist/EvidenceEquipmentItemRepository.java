package com.upc.idbi.gateway.evidence.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceEquipmentItemRepository extends JpaRepository<EvidenceEquipmentItem, Long> {

    List<EvidenceEquipmentItem> findByEvaluationId(Long evaluationId);
}
