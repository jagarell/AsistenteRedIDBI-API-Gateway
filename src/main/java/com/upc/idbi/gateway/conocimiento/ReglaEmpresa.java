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

/** Regla de la empresa (Preguntas y reglas): las base y las que agrega el Asistente IA con aprobación del administrador. */
@Entity
@Table(name = "reglas_empresa")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglaEmpresa {

    /** Código de la regla (R_IP_FIJA, R60…). */
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Observación, Recomendación o Aviso. */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private boolean activa;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(columnDefinition = "TEXT")
    private String cuando;

    /** Pregunta y valor que la activan (para el flujo). */
    @Column(name = "pregunta_id", length = 20)
    private String preguntaId;

    @Column(length = 120)
    private String valor;

    /** base, administrador o asistente_ia. */
    @Column(length = 20)
    private String origen;

    @Column(name = "creada_por", length = 150)
    private String creadaPor;

    @Column(name = "creada_en")
    private LocalDateTime creadaEn;
}
