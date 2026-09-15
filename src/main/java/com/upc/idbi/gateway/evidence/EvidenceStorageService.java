package com.upc.idbi.gateway.evidence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Guarda los archivos de evidencia en disco local (ver app.uploads.dir). */
@Service
public class EvidenceStorageService {

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public String save(Long evaluationId, MultipartFile file) {
        try {
            Path dir = Path.of(uploadsDir, "evidence", String.valueOf(evaluationId));
            Files.createDirectories(dir);

            String extension = extensionOf(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID() + extension;

            Path target = dir.resolve(storedFileName);
            file.transferTo(target);

            return storedFileName;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo de evidencia", e);
        }
    }

    public byte[] read(Long evaluationId, String storedFileName) {
        try {
            Path path = Path.of(uploadsDir, "evidence", String.valueOf(evaluationId), storedFileName);
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de evidencia", e);
        }
    }

    public String urlFor(Long evaluationId, String storedFileName) {
        return "/uploads/evidence/" + evaluationId + "/" + storedFileName;
    }

    public void delete(Long evaluationId, String storedFileName) {
        try {
            Path path = Path.of(uploadsDir, "evidence", String.valueOf(evaluationId), storedFileName);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo de evidencia", e);
        }
    }

    private String extensionOf(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf('.'));
    }
}
