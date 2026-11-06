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

/** Ficha de aprendizaje guardada en la base de conocimiento (el Asistente IA la usa para responder). */
@Entity
@Table(name = "fichas_conocimiento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaConocimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FA-AAAAMMDD-NN */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(length = 20)
    private String tipo;

    @Column(length = 150)
    private String local;

    @Column(length = 150)
    private String tecnico;

    @Column(columnDefinition = "TEXT")
    private String caso;

    @Column(columnDefinition = "TEXT")
    private String aprendizaje;

    /** La ficha completa en JSON. */
    @Column(name = "ficha_json", columnDefinition = "TEXT")
    private String fichaJson;

    @Column(name = "guardada_por", length = 150)
    private String guardadaPor;

    @Column(name = "guardada_en")
    private LocalDateTime guardadaEn;
}
