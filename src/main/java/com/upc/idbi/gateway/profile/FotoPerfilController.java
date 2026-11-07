package com.upc.idbi.gateway.profile;

import com.upc.idbi.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Foto de perfil: JPG o PNG de hasta 3 MB. Se ve en el menú, en Inicio y en Mi perfil; en las listas se siguen viendo las iniciales. */
@RestController
@RequestMapping("/api/profile/foto")
@RequiredArgsConstructor
public class FotoPerfilController {

    static final long MAX_BYTES = 3L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");

    private final UserFotoRepository fotos;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> subir(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TIPOS.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPG o PNG");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto pesa más de 3 MB");
        }
        fotos.save(UserFoto.builder().userId(user.id()).mime(mime).base64(Base64.getEncoder().encodeToString(file.getBytes())).build());
        return Map.of("foto", true);
    }

    @GetMapping
    public ResponseEntity<byte[]> mia(@AuthenticationPrincipal AuthenticatedUser user) {
        return fotos.findById(user.id())
                .map(f -> ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getMime())).body(Base64.getDecoder().decode(f.getBase64())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public Map<String, Object> quitar(@AuthenticationPrincipal AuthenticatedUser user) {
        fotos.findById(user.id()).ifPresent(fotos::delete);
        return Map.of("foto", false);
    }
}
