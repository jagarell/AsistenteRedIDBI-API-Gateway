package com.upc.idbi.gateway.analysis.dto;

import java.util.Map;

/** Respuestas crudas del chat técnico (20 nodos), reenviadas por el Android
 * al completar la evaluación, para que el análisis IA sea real y no un
 * ejemplo fijo. */
public record AnalyzeAnswersRequest(
        Map<String, String> answers
) {
}
