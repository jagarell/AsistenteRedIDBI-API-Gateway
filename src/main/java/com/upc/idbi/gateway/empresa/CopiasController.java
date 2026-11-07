package com.upc.idbi.gateway.empresa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.asistente.XlsxWriter;
import com.upc.idbi.gateway.bitacora.BitacoraService;
import com.upc.idbi.gateway.email.EmailService;
import com.upc.idbi.gateway.minuta.Minuta;
import com.upc.idbi.gateway.minuta.MinutaRepository;
import com.upc.idbi.gateway.reportes.ReportesService;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copias de seguridad (doc 0.0.7 §6): ZIP con los datos de todas las minutas (JSON y Excel), a demanda o programado (diaria, semanal, mensual
 * a la hora elegida). Si hay correo, se avisa de que la copia está lista para descargar desde la app (no se adjunta por tamaño).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CopiasController {

    private final EmpresaService empresaService;
    private final EmpresaRepository empresaRepo;
    private final CopiaSeguridadRepository copias;
    private final MinutaRepository minutas;
    private final ObjectMapper mapper;
    private final BitacoraService bitacora;
    private final EmailService email;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @PostMapping("/api/copias/ahora")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> ahora(@AuthenticationPrincipal AuthenticatedUser user) {
        return fila(generar(user.fullName(), user));
    }

    @GetMapping("/api/copias")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<Map<String, Object>> historial() {
        return copias.findTop30ByOrderByCreadaEnDesc().stream().map(CopiasController::fila).toList();
    }

    @GetMapping("/api/copias/{id}/descargar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) throws IOException {
        CopiaSeguridad c = copias.findById(id).orElseThrow(() -> new IllegalArgumentException("Copia no encontrada"));
        byte[] zip = Files.readAllBytes(Path.of(uploadsDir, "copias", c.getArchivo()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + c.getArchivo() + "\"").body(zip);
    }

    private static Map<String, Object> fila(CopiaSeguridad c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("fecha", c.getCreadaEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        m.put("minutas", c.getMinutas());
        m.put("kb", c.getBytes() == null ? 0 : Math.max(1, c.getBytes() / 1024));
        m.put("por", c.getPor());
        return m;
    }

    /** Cada hora revisa si toca una copia automática según la frecuencia y la hora configuradas. */
    @Scheduled(cron = "0 5 * * * *")
    public void programada() {
        Empresa e = empresaService.get();
        String f = e.getCopiaFrecuencia() == null ? "off" : e.getCopiaFrecuencia();
        if ("off".equals(f) || LocalDateTime.now().getHour() != (e.getCopiaHora() == null ? 2 : e.getCopiaHora())) {
            return;
        }
        LocalDateTime u = e.getCopiaUltima();
        LocalDateTime ahora = LocalDateTime.now();
        boolean toca = u == null || switch (f) {
            case "diaria" -> u.toLocalDate().isBefore(ahora.toLocalDate());
            case "semanal" -> u.toLocalDate().isBefore(ahora.toLocalDate().minusDays(6));
            default -> u.toLocalDate().isBefore(ahora.toLocalDate().minusMonths(1).plusDays(1));
        };
        if (toca) {
            try {
                CopiaSeguridad c = generar("Automática", null);
                if (e.getCopiaCorreo() != null && !e.getCopiaCorreo().isBlank()) {
                    email.sendSimple(e.getCopiaCorreo(), "Copia de seguridad lista · " + empresaService.nombre(),
                            "Se generó la copia de seguridad (" + c.getMinutas() + " minutas). Descárgala desde Perfil y configuración › Copia de seguridad.");
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo generar o avisar la copia automática", ex);
            }
        }
    }

    synchronized CopiaSeguridad generar(String por, AuthenticatedUser user) {
        List<Minuta> todas = minutas.findAllByOrderByCreatedAtDesc();
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(bo)) {
            List<Map<String, Object>> json = new ArrayList<>();
            List<List<Object>> filas = new ArrayList<>();
            for (Minuta m : todas) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", m.getId());
                d.put("cliente", m.getClientName());
                d.put("direccion", m.getAddress());
                d.put("tecnico", m.getTechnicianName());
                d.put("estado", ReportesService.estadoDe(m));
                d.put("tipo", m.getTipo());
                d.put("version", m.versionOrOne());
                d.put("puntaje", m.getPuntaje());
                d.put("etapa", m.getEtapa());
                d.put("creada", String.valueOf(m.getCreatedAt()));
                d.put("resumen", m.getSummary());
                d.put("contenido", m.getContentJson());
                d.put("topologia", m.getTopologyJson());
                json.add(d);
                filas.add(new ArrayList<>(Arrays.asList(m.getClientName(), m.getTechnicianName(), ReportesService.estadoDe(m), m.getPuntaje(),
                        String.valueOf(m.getCreatedAt()).substring(0, Math.min(10, String.valueOf(m.getCreatedAt()).length())), m.getAddress())));
            }
            put(z, "minutas.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json));
            put(z, "minutas.xlsx", XlsxWriter.hoja("Copia de seguridad · " + empresaService.nombre(), "Generada el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    List.of("Local", "Técnico", "Estado", "Puntaje", "Fecha", "Dirección"), filas));
            z.finish();
            byte[] zip = bo.toByteArray();
            String nombre = "copia-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip";
            Path dir = Path.of(uploadsDir, "copias");
            Files.createDirectories(dir);
            Files.write(dir.resolve(nombre), zip);
            Empresa e = empresaService.get();
            e.setCopiaUltima(LocalDateTime.now());
            empresaRepo.save(e);
            CopiaSeguridad c = copias.save(CopiaSeguridad.builder().creadaEn(LocalDateTime.now()).archivo(nombre).minutas(todas.size()).bytes((long) zip.length).por(por).build());
            bitacora.registrar(user, null, "copia", "Generó una copia de seguridad (" + todas.size() + " minutas)");
            return c;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar la copia de seguridad", ex);
        }
    }

    private static void put(ZipOutputStream z, String nombre, byte[] datos) throws IOException {
        z.putNextEntry(new ZipEntry(nombre));
        z.write(datos);
        z.closeEntry();
    }
}
