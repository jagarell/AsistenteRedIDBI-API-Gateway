package com.upc.idbi.gateway.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudAccesoRepository extends JpaRepository<SolicitudAcceso, Long> {

    List<SolicitudAcceso> findBySolicitanteIdOrderByCreadaEnDesc(Long solicitanteId);

    Optional<SolicitudAcceso> findFirstBySolicitanteIdAndCodigoAndEstado(Long solicitanteId, String codigo, String estado);
}
