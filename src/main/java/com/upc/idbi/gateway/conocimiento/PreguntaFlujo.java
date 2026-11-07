package com.upc.idbi.gateway.conocimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Pregunta agregada al flujo del Chat IA (Preguntas y reglas). Las preguntas base viven en el motor del Chat IA. */
@Entity
@Table(name = "preguntas_flujo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreguntaFlujo {

    @Id
    @Column(length = 20)
    private String id;

    /** Sección del flujo (B Negocio y canales, E Impresoras…). */
    @Column(name = "seccion_id", length = 4)
    private String seccionId;

    @Column(length = 80)
    private String seccion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(length = 30)
    private String tipo;

    /** Opciones separadas por «|». */
    @Column(columnDefinition = "TEXT")
    private String opciones;

    @Column(columnDefinition = "TEXT")
    private String cuando;

    @Column(length = 20)
    private String origen;

    @Column(name = "creada_por", length = 150)
    private String creadaPor;

    @Column(name = "creada_en")
    private LocalDateTime creadaEn;

    /** Posición dentro de su sección (las preguntas agregadas se pueden subir o bajar). */
    private Integer orden;
}
