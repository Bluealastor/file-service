package com.media.file_service.controller;

import com.media.file_service.model.CopyRequest;
import com.media.file_service.service.FileCopyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Controller per la copia di file selezionati verso una cartella del NAS
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileCopyController {

    private final FileCopyService fileCopyService;

    // POST /api/files/copy
    // Copia i file indicati in sourcePaths nella cartella destinationFolder.
    // Restituisce la lista dei percorsi dei file copiati con successo
    @PostMapping("/copy")
    public ResponseEntity<List<String>> copyFiles(@Valid @RequestBody CopyRequest request) {
        List<String> copied = fileCopyService.copyFiles(request);
        return ResponseEntity.ok(copied);
    }
}
