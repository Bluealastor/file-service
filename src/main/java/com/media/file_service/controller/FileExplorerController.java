package com.media.file_service.controller;

import com.media.file_service.model.FileMetadataResponse;
import com.media.file_service.model.SmbConnectionRequest;
import com.media.file_service.service.LocalFileService;
import com.media.file_service.service.SmbFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Controller per l'esplorazione del filesystem: lista file e metadati
// da sorgente locale (USB/NAS) o SMB (PC remoto sulla rete)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileExplorerController {

    private final LocalFileService localFileService;
    private final SmbFileService smbFileService;

    // GET /api/files/local?path=Foto/2024
    // Elenca i file nella cartella specificata, relativa al basePath configurato in YAML.
    // Usa GET perché non ci sono credenziali sensibili: il path locale è sicuro in query string
    @GetMapping("/local")
    public List<FileMetadataResponse> listLocal(@RequestParam String path) {
        return localFileService.listFiles(path);
    }

    // POST /api/files/smb
    // Elenca i file nella share SMB specificata nel body della richiesta.
    // Usa POST (non GET) perché il body contiene la password — mai in query string,
    // perché gli URL finiscono nei log di server, proxy e nella history del browser
    @PostMapping("/smb")
    public List<FileMetadataResponse> listSmb(@Valid @RequestBody SmbConnectionRequest request) {
        return smbFileService.listFiles(request);
    }
}
