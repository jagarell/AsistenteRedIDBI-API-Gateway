package com.upc.idbi.gateway.notification;

import jakarta.persistence.*;
import lombok.*;

/** «Avisarme cuando…» (doc 0.0.7 §47): qué avisos quiere recibir cada usuario. Sin fila, todos activos. */
@Entity
@Table(name = "user_avisos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvisos {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** JSON {clave: boolean}. */
    @Column(columnDefinition = "TEXT")
    private String json;
}
