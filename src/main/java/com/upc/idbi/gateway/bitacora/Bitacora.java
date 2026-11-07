package com.upc.idbi.gateway.bitacora;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Bitácora de trazabilidad (doc 0.0.7 §1 y §24). Solo de inserción: no se edita ni se borra. */
@Entity
@Table(name = "bitacora")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minuta_id")
    private Long minutaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_nombre", length = 150)
    private String usuarioNombre;

    @Column(length = 20)
    private String rol;

    /** crear, editar, compartir, descargar, solicitar, validar, rechazar, aviso, empresa, copia… */
    @Column(length = 30)
    private String tipo;

    @Column(length = 400)
    private String accion;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;
}
