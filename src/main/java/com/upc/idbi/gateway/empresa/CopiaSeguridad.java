package com.upc.idbi.gateway.empresa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Historial de copias de seguridad: cada una es un ZIP con las minutas (JSON y Excel) guardado en el servidor. */
@Entity
@Table(name = "copias_seguridad")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopiaSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(length = 200)
    private String archivo;

    private Integer minutas;

    private Long bytes;

    @Column(length = 150)
    private String por;
}
