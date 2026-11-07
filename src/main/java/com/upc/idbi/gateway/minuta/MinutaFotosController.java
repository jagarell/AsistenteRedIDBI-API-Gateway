package com.upc.idbi.gateway.minuta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Fotos de una minuta redactada con el Asistente IA (doc 0.0.7 §6): cada foto lleva el tipo de evidencia que el técnico eligió
 * (Frontis del local, Speedtest…). Se guardan en disco y quedan listadas en el contenido de la minuta.
 */
@RestController
@RequestMapping("/api/minutas/{id}/fotos")
@RequiredArgsConstructor
public class MinutaFotosController {

    private final MinutaRepository minutas;
    private final ObjectMapper mapper;
    private final com.upc.idbi.gateway.bitacora.BitacoraService bitacora;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> subir(@PathVariable Long id, @RequestParam String tipo, @RequestParam("archivo") MultipartFile archivo,
                                     @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        Minuta m = minutas.findById(id).orElseThrow(() -> new IllegalArgumentException("Minuta no encontrada"));
        if (!user.isAdmin() && !Objects.equals(m.getTechnicianId(), user.id())) {
            throw new AccessDeniedException("Solo el dueño de la minuta sube sus fotos");
        }
        if (archivo.isEmpty() || archivo.getSize() > 8L * 1024 * 1024) {
            throw new IllegalArgumentException("La foto debe pesar hasta 8 MB");
        }
        Path dir = Path.of(uploadsDir, "minutas", String.valueOf(id));
        Files.createDirectories(dir);
        String nombre = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6) + ".jpg";
        Files.write(dir.resolve(nombre), archivo.getBytes());
        Map<String, Object> c = leer(m.getContentJson());
        List<Map<String, Object>> fotos = c.get("fotos") instanceof List<?> l ? new ArrayList<>((List<Map<String, Object>>) l) : new ArrayList<>();
        fotos.add(Map.of("tipo", tipo, "archivo", nombre));
        c.put("fotos", fotos);
        // Lo que ya se subió deja de estar «por subir».
        if (c.get("evidenciasFaltantes") instanceof List<?> f) {
            List<Object> resto = new ArrayList<>(f);
            resto.removeIf(x -> String.valueOf(x).equalsIgnoreCase(tipo));
            c.put("evidenciasFaltantes", resto);
        }
        try {
            m.setContentJson(mapper.writeValueAsString(c));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        minutas.save(m);
        bitacora.registrar(user, id, "editar", "Subió la foto «" + tipo + "» a la minuta de " + m.getClientName());
        return Map.of("archivo", nombre, "tipo", tipo);
    }

    @GetMapping("/{archivo}")
    public ResponseEntity<byte[]> ver(@PathVariable Long id, @PathVariable String archivo) throws IOException {
        if (archivo.contains("/") || archivo.contains("..")) {
            throw new IllegalArgumentException("Archivo no válido");
        }
        Path p = Path.of(uploadsDir, "minutas", String.valueOf(id), archivo);
        if (!Files.exists(p)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(Files.readAllBytes(p));
    }

    private Map<String, Object> leer(String json) {
        try {
            return json == null || json.isBlank() ? new LinkedHashMap<>() : mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
