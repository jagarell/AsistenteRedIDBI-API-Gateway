package com.upc.idbi.gateway.evidence.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceAreaRepository extends JpaRepository<EvidenceArea, Long> {

    List<EvidenceArea> findByEvaluationId(Long evaluationId);

    boolean existsByEvaluationId(Long evaluationId);
}
