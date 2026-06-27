package com.media.file_service.controller;

import com.media.file_service.model.DownloadRequest;
import com.media.file_service.service.FileCopyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

// Controller per il download di file dal NAS verso il client
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileDownloadController {

    private final FileCopyService fileCopyService;

    // POST /api/files/download
    // Invia il file in streaming nella risposta HTTP.
    // Usa POST invece di GET per due motivi:
    // 1. Il path non finisce nell'URL (log, history, proxy)
    // 2. Coerenza con gli altri endpoint che ricevono dati sensibili nel body
    //
    // Nota: con POST il browser non può scaricare il file navigando direttamente a un URL.
    // Il frontend dovrà usare fetch() + Blob per attivare il download:
    //
    //   fetch('/api/files/download', { method: 'POST', body: JSON.stringify({ path: '...' }) })
    //     .then(res => res.blob())
    //     .then(blob => {
    //       const url = URL.createObjectURL(blob);
    //       const a = document.createElement('a');
    //       a.href = url; a.download = 'nomefile.ext'; a.click();
    //     });
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@Valid @RequestBody DownloadRequest request) {
        Path filePath = fileCopyService.resolveDownloadPath(request.getPath());
        Resource resource = new FileSystemResource(filePath);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
