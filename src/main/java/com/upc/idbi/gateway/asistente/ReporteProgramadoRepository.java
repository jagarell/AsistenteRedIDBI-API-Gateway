package com.upc.idbi.gateway.asistente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReporteProgramadoRepository extends JpaRepository<ReporteProgramado, Long> {

    List<ReporteProgramado> findByUserIdOrderByCreadoEnDesc(Long userId);
}
