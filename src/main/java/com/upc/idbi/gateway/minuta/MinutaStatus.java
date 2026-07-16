package com.upc.idbi.gateway.minuta;

/**
 * Estados de una minuta técnica.
 * <ul>
 *     <li>{@link #BORRADOR}: en elaboración. Cualquier técnico puede verla y
 *     continuarla (para que otro técnico pueda terminar lo que un compañero
 *     dejó a medias).</li>
 *     <li>{@link #COMPLETA}: el técnico la marcó como terminada; queda a la
 *     espera de validación del supervisor.</li>
 *     <li>{@link #VALIDADA}: el supervisor la revisó y aprobó. Es el único
 *     estado que puede fijar un usuario con rol SUPERVISOR.</li>
 * </ul>
 */
public enum MinutaStatus {
    BORRADOR,
    COMPLETA,
    VALIDADA
}
