package com.upc.idbi.gateway.empresa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Datos de la empresa (una sola fila): nombre y logo de la minuta, ambiente demo/producción y copias de seguridad. */
@Entity
@Table(name = "empresa")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Empresa {

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Logo en base64 (PNG o JPG, hasta 2 MB) y su tipo; nulo = sin logo (sale solo el nombre). */
    @Column(name = "logo_base64", columnDefinition = "TEXT")
    private String logoBase64;

    @Column(name = "logo_mime", length = 40)
    private String logoMime;

    /** Nulo = modo demo. Al pasar a producción es irreversible. */
    @Column(name = "produccion_desde")
    private LocalDateTime produccionDesde;

    @Column(name = "produccion_por", length = 150)
    private String produccionPor;

    /** off | diaria | semanal | mensual. */
    @Column(name = "copia_frecuencia", length = 10)
    private String copiaFrecuencia;

    @Column(name = "copia_hora")
    private Integer copiaHora;

    @Column(name = "copia_correo", length = 150)
    private String copiaCorreo;

    @Column(name = "copia_incluye_fotos")
    private Boolean copiaIncluyeFotos;

    @Column(name = "copia_ultima")
    private LocalDateTime copiaUltima;
}
