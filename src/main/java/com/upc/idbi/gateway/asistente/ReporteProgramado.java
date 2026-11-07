package com.upc.idbi.gateway.asistente;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Reporte que el Asistente IA envía solo («cada fin de mes»): la consulta queda guardada y llega por notificación. */
@Entity
@Table(name = "reportes_programados")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteProgramado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** fin_de_mes. */
    @Column(length = 20)
    private String frecuencia;

    @Column(name = "consulta_json", columnDefinition = "TEXT")
    private String consultaJson;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "ultimo_envio")
    private LocalDateTime ultimoEnvio;
}
