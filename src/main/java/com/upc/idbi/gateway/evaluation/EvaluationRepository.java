package com.upc.idbi.gateway.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByStatus(EvaluationStatus status);

    /** Todo menos lo anulado (listas e historial). */
    List<Evaluation> findByStatusNot(EvaluationStatus status);

    long countByStatusNot(EvaluationStatus status);
}
