package com.media.file_service.controller;

import com.media.file_service.service.FileCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Controller per l'upload di file dal browser verso il NAS
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileCopyService fileCopyService;

    // POST /api/files/upload
    // Riceve un file multipart e una sottocartella opzionale di destinazione.
    //
    // Usa @RequestPart invece di @RequestParam per entrambi i campi:
    // @RequestParam legge sia dal body multipart CHE dalla query string dell'URL
    // (es. /upload?folder=Foto) — il che esporrebbe il percorso nei log di server e proxy.
    // @RequestPart legge SOLO dal body multipart, mai dall'URL.
    //
    // Esempio di chiamata con fetch():
    //   const form = new FormData();
    //   form.append('file', fileBlob);
    //   form.append('folder', 'Foto/2024');  // opzionale
    //   fetch('/api/files/upload', { method: 'POST', body: form });
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "folder", required = false) String folder) {

        String savedPath = fileCopyService.uploadFile(file, folder);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPath);
    }
}
