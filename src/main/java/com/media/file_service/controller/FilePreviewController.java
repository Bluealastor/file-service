package com.media.file_service.controller;

import com.media.file_service.service.FileCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Controller per lo streaming inline di file (anteprima foto e video).
//
// Usa StreamingResponseBody per entrambi i casi (file completo e range):
// scrive direttamente sull'OutputStream HTTP senza caricare nulla in memoria.
// Supporta HTTP Range requests (RFC 7233) → seeking video preciso nel browser.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FilePreviewController {

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    private final FileCopyService fileCopyService;

    @GetMapping("/preview")
    public ResponseEntity<StreamingResponseBody> preview(
            @RequestParam String path,
            @RequestHeader HttpHeaders requestHeaders) throws Exception {

        Path filePath = fileCopyService.resolveDownloadPath(path);
        long fileSize = Files.size(filePath);
        String mime   = detectContentType(filePath.getFileName().toString());
        String disp   = "inline; filename=\"" + filePath.getFileName() + "\"";

        List<HttpRange> ranges = requestHeaders.getRange();

        if (ranges.isEmpty()) {
            // ── File completo (200 OK) ────────────────────────────────────────
            StreamingResponseBody body = out -> {
                try (InputStream is = Files.newInputStream(filePath)) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disp)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .contentType(MediaType.parseMediaType(mime))
                    .body(body);
        }

        // ── Partial Content (206) ─────────────────────────────────────────────
        HttpRange range  = ranges.get(0);
        long start  = range.getRangeStart(fileSize);
        long end    = range.getRangeEnd(fileSize);
        long length = end - start + 1;

        StreamingResponseBody body = out -> {
            try (InputStream is = Files.newInputStream(filePath)) {
                long toSkip = start;
                while (toSkip > 0) {
                    long n = is.skip(toSkip);
                    if (n <= 0) break;
                    toSkip -= n;
                }
                byte[] buf = new byte[BUFFER_SIZE];
                long remaining = length;
                int read;
                while (remaining > 0
                        && (read = is.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, read);
                    remaining -= read;
                }
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, disp)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
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
