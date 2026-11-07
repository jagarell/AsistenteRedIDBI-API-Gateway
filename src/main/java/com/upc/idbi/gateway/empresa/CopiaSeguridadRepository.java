package com.upc.idbi.gateway.empresa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CopiaSeguridadRepository extends JpaRepository<CopiaSeguridad, Long> {

    List<CopiaSeguridad> findTop30ByOrderByCreadaEnDesc();
}
