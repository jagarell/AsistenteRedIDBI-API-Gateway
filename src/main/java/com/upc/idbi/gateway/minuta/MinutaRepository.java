package com.upc.idbi.gateway.minuta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinutaRepository extends JpaRepository<Minuta, Long> {

    List<Minuta> findByStatusOrderByCreatedAtDesc(MinutaStatus status);

    List<Minuta> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);

    List<Minuta> findAllByOrderByCreatedAtDesc();
}
