package com.media.file_service.controller;

import com.media.file_service.service.FileCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

// Controller per lo streaming inline di file (anteprima foto e video).
// Usa GET invece di POST perché video_player (Flutter) e i widget immagine
// devono poter accedere al file tramite URL + header Authorization.
//
// La differenza rispetto a FileDownloadController:
//   - Download: Content-Disposition: attachment → il browser scarica il file
//   - Preview:  Content-Disposition: inline    → il browser/player lo mostra direttamente
//
// Spring gestisce automaticamente le HTTP range requests (necessarie per seek video)
// quando si restituisce un FileSystemResource in un ResponseEntity.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FilePreviewController {

    private final FileCopyService fileCopyService;

    // GET /api/files/preview?path=Foto/2024/IMG_001.jpg
    // Il path è relativo al basePath configurato nel file-service.
    // La validazione JWT avviene a livello di api-gateway prima che la richiesta arrivi qui.
    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(@RequestParam String path) {
        Path filePath = fileCopyService.resolveDownloadPath(path);
        Resource resource = new FileSystemResource(filePath);

        String contentType = detectContentType(filePath.getFileName().toString());

        return ResponseEntity.ok()
                // "inline" dice al client di mostrare il file, non di scaricarlo
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filePath.getFileName().toString() + "\"")
                // Necessario per lo seek nei video: il client può richiedere
                // solo una porzione del file con "Range: bytes=X-Y"
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    // Rileva il Content-Type in base all'estensione del file.
    // Necessario perché i player/browser devono sapere come decodificare il flusso.
    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.matches(".*\\.(jpg|jpeg)$")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) return "image/heif";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        if (lower.endsWith(".mkv"))  return "video/x-matroska";
        if (lower.endsWith(".avi"))  return "video/x-msvideo";
        if (lower.endsWith(".m4v"))  return "video/x-m4v";
        if (lower.endsWith(".wmv"))  return "video/x-ms-wmv";
        return "application/octet-stream";
    }
}
