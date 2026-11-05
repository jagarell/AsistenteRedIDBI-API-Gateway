package com.upc.idbi.gateway.conocimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Reglas base de la empresa (las mismas del motor del Chat IA); R60 viene desactivada. Solo se cargan si la tabla está vacía. */
@Component
@RequiredArgsConstructor
public class ReglasSeeder implements CommandLineRunner {

    private final ReglaEmpresaRepository repository;

    static List<ReglaEmpresa> base() {
        LocalDateTime t = LocalDateTime.now();
        return List.of(
                r("SISIMP", "Sistema de impresiones", "Aviso", true, "", "", t),
                r("OBRA", "Segunda visita técnica", "Recomendación", true, "Recomienda una segunda visita técnica cuando el local esté terminado.", "El local está en obra o remodelación", t),
                r("SIN_INT", "Sin internet", "Observación", true, "Observación: sin internet no se puede iniciar la operación.", "El local no tiene internet", t),
                r("CONTRATAR", "Contratar internet antes de implementar", "Recomendación", true, "Recomienda contratar internet antes de implementar.", "No hay internet contratado", t),
                r("R56", "TP-Link con entrada de chip", "Recomendación", true, "Recomienda usar el chip como respaldo de internet.", "El router es TP-Link con entrada de chip", t),
                r("CHIP_PEND", "Prueba del chip pendiente", "Aviso", true, "Aviso: la prueba del chip queda pendiente.", "Hay chip pero no se probó", t),
                r("R67", "El internet no llega a lo contratado", "Observación", true, "Observación: el internet no llega a lo contratado.", "El test de velocidad da menos de lo contratado", t),
                r("R60", "PC temporal", "Recomendación", false, "Recomienda reemplazarla por un equipo definitivo.", "La PC de caja es temporal", t),
                r("R_RESERVA_IP", "Reservar las IP de las impresoras", "Recomendación", true, "Pedir al proveedor de internet que reserve en el router las IP de las impresoras.", "Hay varios equipos conectados o 3 o más impresoras por red", t),
                r("R_UPS", "UPS para los equipos de red", "Recomendación", true, "Conectar el router, el switch y el equipo de caja a un UPS.", "El cliente indica intermitencias de energía", t),
                r("R_ROUTER_ENC", "Router en un espacio cerrado", "Aviso", true, "El local es apto, pero el equipo técnico debe darle seguimiento para que no tenga intermitencias de internet.",
                        "El router está en un cuarto pequeño con puertas cerca al piso o dentro de un mueble cerrado con puertas", t));
    }

    private static ReglaEmpresa r(String id, String n, String tipo, boolean on, String txt, String cuando, LocalDateTime t) {
        return ReglaEmpresa.builder().id(id).nombre(n).tipo(tipo).activa(on).texto(txt).cuando(cuando).origen("base").creadaEn(t).build();
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.saveAll(base());
        }
    }
}
