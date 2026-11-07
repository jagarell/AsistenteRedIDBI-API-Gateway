package com.upc.idbi.gateway.bitacora;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Long> {

    List<Bitacora> findTop200ByOrderByTsDesc();

    List<Bitacora> findByMinutaIdOrderByTsDesc(Long minutaId);

    List<Bitacora> findByUsuarioIdOrderByTsDesc(Long usuarioId);

    boolean existsByMinutaIdAndUsuarioIdAndAccionAndTsAfter(Long minutaId, Long usuarioId, String accion, LocalDateTime desde);
}
