package com.upc.idbi.gateway.asistente;

import jakarta.persistence.*;
import lombok.*;

/** Prompts frecuentes ★ del Asistente IA: de cada usuario, en su cuenta (se ven igual en la app y en la web). */
@Entity
@Table(name = "prompts_frecuentes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptsFrecuentes {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String json;
}
