package com.media.file_service.controller;

import com.media.file_service.service.FileCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Controller per lo streaming inline di file (anteprima foto e video).
//
// Supporta HTTP Range requests (RFC 7233) per lo streaming video:
//   - Senza Range: restituisce il file completo (200 OK)
//   - Con Range: restituisce solo la porzione richiesta (206 Partial Content)
//
// Questo è essenziale per:
//   - Seeking nel video (andare avanti/indietro senza scaricare tutto)
//   - Buffering intelligente del browser (scarica solo la parte che sta per riprodurre)
//   - Qualità video stabile anche su connessioni lente
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FilePreviewController {

    private final FileCopyService fileCopyService;

    // GET /api/files/preview?path=Video/film.mp4
    // Accetta l'header opzionale "Range: bytes=X-Y" per lo streaming parziale.
    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview(
            @RequestParam String path,
            @RequestHeader HttpHeaders requestHeaders) throws IOException {

        Path filePath  = fileCopyService.resolveDownloadPath(path);
        long fileSize  = Files.size(filePath);
        String mime    = detectContentType(filePath.getFileName().toString());
        String disposition = "inline; filename=\"" + filePath.getFileName() + "\"";

        List<HttpRange> ranges = requestHeaders.getRange();

        if (ranges.isEmpty()) {
            // ── Richiesta normale: invia il file completo (200 OK) ────────────
            byte[] data = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .contentType(MediaType.parseMediaType(mime))
                    .body(data);
        }

        // ── Range request: invia solo la porzione richiesta (206 Partial Content) ──
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileSize);
        long end   = range.getRangeEnd(fileSize);
        long length = end - start + 1;

        byte[] data = new byte[(int) length];
        try (InputStream is = Files.newInputStream(filePath)) {
            long skipped = is.skip(start);
            if (skipped < start) throw new IOException("Skip failed");
            int read = 0, remaining = (int) length;
            while (remaining > 0) {
                int n = is.read(data, read, remaining);
                if (n < 0) break;
                read      += n;
                remaining -= n;
            }
        }

        String contentRange = "bytes " + start + "-" + end + "/" + fileSize;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(length))
                .contentType(MediaType.parseMediaType(mime))
                .body(data);
    }

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
        if (lower.endsWith(".insv")) return "video/mp4";
        if (lower.endsWith(".lrv"))  return "video/mp4";
        return "application/octet-stream";
    }
}
