package com.upc.idbi.gateway.minuta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Historial de versiones de una minuta (cada actualización por el Asistente IA). Nada se borra. */
@Entity
@Table(name = "minuta_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minuta_id", nullable = false)
    private Long minutaId;

    @Column(name = "version_num", nullable = false)
    private Integer version;

    /** Lo que cambió, una línea por cambio. */
    @Column(columnDefinition = "TEXT")
    private String resumen;

    /** Contenido anterior (para restaurar). */
    @Column(name = "content_json_prev", columnDefinition = "TEXT")
    private String contentJsonPrev;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
