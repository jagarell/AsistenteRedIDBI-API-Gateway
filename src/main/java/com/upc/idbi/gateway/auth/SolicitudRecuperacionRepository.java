package com.upc.idbi.gateway.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudRecuperacionRepository extends JpaRepository<SolicitudRecuperacion, Long> {

    List<SolicitudRecuperacion> findByEstadoOrderByCreadaEnDesc(String estado);

    List<SolicitudRecuperacion> findByUserIdAndEstado(Long userId, String estado);
}
