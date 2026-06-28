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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Controller per lo streaming inline di file (anteprima foto e video).
//
// Supporta HTTP Range requests (RFC 7233) per lo streaming video senza caricare
// il file in memoria — usa StreamingResponseBody per scrivere direttamente sullo stream:
//   - Senza Range → 200 OK, file completo (via FileSystemResource, Spring gestisce lo stream)
//   - Con Range   → 206 Partial Content, solo la porzione richiesta (chunked stream)
//
// Questo è essenziale per seeking, buffering intelligente e qualità video stabile.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FilePreviewController {

    private final FileCopyService fileCopyService;

    @GetMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestParam String path,
            @RequestHeader HttpHeaders requestHeaders) throws IOException {

        Path filePath   = fileCopyService.resolveDownloadPath(path);
        long fileSize   = Files.size(filePath);
        String mime     = detectContentType(filePath.getFileName().toString());
        String disp     = "inline; filename=\"" + filePath.getFileName() + "\"";

        List<HttpRange> ranges = requestHeaders.getRange();

        if (ranges.isEmpty()) {
            // ── Richiesta completa: FileSystemResource streama efficientemente ──
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disp)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .contentType(MediaType.parseMediaType(mime))
                    .body(resource);
        }

        // ── Range request: stream solo la porzione richiesta ──────────────────
        HttpRange range  = ranges.get(0);
        long start  = range.getRangeStart(fileSize);
        long end    = range.getRangeEnd(fileSize);
        long length = end - start + 1;

        // StreamingResponseBody scrive direttamente sull'OutputStream HTTP —
        // nessun buffer in memoria, funziona anche per file da decine di GB
        StreamingResponseBody body = outputStream -> {
            try (InputStream is = Files.newInputStream(filePath)) {
                long toSkip = start;
                while (toSkip > 0) {
                    long skipped = is.skip(toSkip);
                    if (skipped <= 0) break;
                    toSkip -= skipped;
                }
                byte[] buf = new byte[64 * 1024]; // 64 KB per chunk
                long remaining = length;
                int read;
                while (remaining > 0
                        && (read = is.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                    outputStream.write(buf, 0, read);
                    remaining -= read;
                }
            }
        };

        String contentRange = "bytes " + start + "-" + end + "/" + fileSize;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, disp)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(length))
                .contentType(MediaType.parseMediaType(mime))
                .body(body);
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
