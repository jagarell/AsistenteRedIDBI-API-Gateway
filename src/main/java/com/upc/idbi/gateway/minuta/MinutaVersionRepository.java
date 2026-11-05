package com.upc.idbi.gateway.minuta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MinutaVersionRepository extends JpaRepository<MinutaVersion, Long> {
    List<MinutaVersion> findByMinutaIdOrderByVersionDesc(Long minutaId);
}
