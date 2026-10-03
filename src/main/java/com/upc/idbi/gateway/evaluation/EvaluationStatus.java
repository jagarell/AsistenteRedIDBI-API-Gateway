package com.upc.idbi.gateway.evaluation;

public enum EvaluationStatus {
    COMPLETADO,
    BORRADOR,
    ENVIADO,
    EN_ANALISIS,
    /** Descartada a mano por el técnico (solo desde BORRADOR). No aparece en listas ni en los conteos. */
    ANULADA
}
