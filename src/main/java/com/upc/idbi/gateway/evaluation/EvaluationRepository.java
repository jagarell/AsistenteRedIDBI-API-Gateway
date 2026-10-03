package com.upc.idbi.gateway.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByStatus(EvaluationStatus status);

    /** Todo menos lo anulado (listas e historial). */
    @Query("select e from Evaluation e where e.annulled is null or e.annulled = false")
    List<Evaluation> findActive();

    @Query("select count(e) from Evaluation e where e.annulled is null or e.annulled = false")
    long countActive();
}
