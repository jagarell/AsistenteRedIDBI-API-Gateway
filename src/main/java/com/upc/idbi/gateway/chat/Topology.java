package com.upc.idbi.gateway.chat;

import java.util.List;

/**
 * Topología de red estructurada generada por el motor (FastAPI) a partir de las
 * respuestas del chat. El gateway solo la reenvía hacia la app.
 */
public record Topology(
        List<TopologyNode> nodes,
        List<TopologyLink> links
) {
    public record TopologyNode(
            String id,
            String label,
            String type,
            Integer level
    ) {
    }

    public record TopologyLink(
            String source,
            String target,
            String connectionType,
            String status
    ) {
    }
}
