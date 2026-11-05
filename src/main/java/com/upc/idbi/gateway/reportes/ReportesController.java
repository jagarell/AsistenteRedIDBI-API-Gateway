package com.upc.idbi.gateway.reportes;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService service;

    /** GET /api/reportes?vista=general|tecnicos|mis&desde=&hasta=&tecnico=&supervisor=&rubro=&estado=&criticidad=&esfuerzo=&plazo= */
    @GetMapping("/api/reportes")
    public Map<String, Object> reporte(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(required = false) String vista,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(required = false) Set<String> tecnico,
                                       @RequestParam(required = false) Set<String> supervisor,
                                       @RequestParam(required = false) Set<String> rubro,
                                       @RequestParam(required = false) Set<String> estado,
                                       @RequestParam(required = false) Set<String> criticidad,
                                       @RequestParam(required = false) Set<String> esfuerzo,
                                       @RequestParam(required = false) Set<String> plazo) {
        return service.reporte(user, vista, new ReportesService.Filtros(desde, hasta, nz(tecnico), nz(supervisor), nz(rubro), nz(estado), nz(criticidad), nz(esfuerzo), nz(plazo)));
    }

    private static Set<String> nz(Set<String> s) {
        return s == null ? Set.of() : s;
    }
}
