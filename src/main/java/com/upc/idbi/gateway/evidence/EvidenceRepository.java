package com.upc.idbi.gateway.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findByEvaluationIdOrderByUploadedAtDesc(Long evaluationId);

    List<Evidence> findByAreaIdOrderByUploadedAtDesc(Long areaId);

    List<Evidence> findByEquipmentItemIdOrderByUploadedAtDesc(Long equipmentItemId);

    List<Evidence> findByEvaluationIdAndEvidenceCodeIsNotNullOrderByUploadedAtAsc(Long evaluationId);

    List<Evidence> findByEvaluationIdAndEvidenceCodeAndChatScope(Long evaluationId, String evidenceCode, String chatScope);

    boolean existsByAreaId(Long areaId);

    boolean existsByEquipmentItemId(Long equipmentItemId);
}
