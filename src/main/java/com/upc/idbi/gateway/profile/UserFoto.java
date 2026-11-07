package com.upc.idbi.gateway.profile;

import jakarta.persistence.*;
import lombok.*;

/** Foto de perfil (doc 0.0.7 §35). Va en su propia tabla para no cargarla en cada consulta de usuarios. */
@Entity
@Table(name = "user_foto")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFoto {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 40)
    private String mime;

    @Column(name = "base64", columnDefinition = "TEXT")
    private String base64;
}
