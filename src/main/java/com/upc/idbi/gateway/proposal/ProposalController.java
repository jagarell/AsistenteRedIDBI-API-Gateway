package com.upc.idbi.gateway.proposal;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/proposal")
@CrossOrigin(origins = "*")
public class ProposalController {

    @GetMapping
    public Map<String, Object> getProposal(@PathVariable Long evaluationId) {
        return Map.of(
                "restaurantName", "Restaurante El Rincón",
                "status", "Borrador",
                "date", "20 Jun 2026",
                "version", "v1.0",
                "total", 3150,
                "equipment", List.of(
                        Map.of("name", "Cisco ISR 1100 Series Router", "description", "Router principal con firewall integrado", "price", 450, "quantity", 1),
                        Map.of("name", "Cisco Catalyst 2960-X Switch", "description", "Switch administrable 24 puertos PoE", "price", 760, "quantity", 2),
                        Map.of("name", "Ubiquiti UniFi AP AC PRO", "description", "Punto de acceso WiFi 6", "price", 1000, "quantity", 4)
                ),
                "recommendations", List.of(
                        "Implementar segmentación VLAN",
                        "Configurar QoS para tráfico crítico",
                        "Instalar 2 APs adicionales",
                        "Implementar redundancia de enlace"
                ),
                "pdfName", "Propuesta_ElRincon_v1.0.pdf",
                "pdfSizeMb", 2.4
        );
    }

    @PutMapping
    public Map<String, String> updateProposal(@PathVariable Long evaluationId, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Propuesta actualizada correctamente");
    }

    @PostMapping("/send")
    public Map<String, String> sendProposal(@PathVariable Long evaluationId, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Propuesta enviada correctamente");
    }
}