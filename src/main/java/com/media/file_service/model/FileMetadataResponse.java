package com.media.file_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

// DTO restituito al client con tutte le informazioni su un file o cartella.
// @Builder permette di costruire l'oggetto con il pattern builder: FileMetadataResponse.builder().fileName(...).build()
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadataResponse {

    // Nome del file o cartella (es. "foto.jpg")
    private String fileName;

    // Percorso completo (es. "/mnt/disks/usb1/foto.jpg")
    private String fullPath;

    // Dimensione in byte (0 per le cartelle)
    private long size;

    // Data e ora dell'ultima modifica
    private LocalDateTime lastModified;

    // Tipo: IMAGE, VIDEO, DOCUMENT, OTHER, DIRECTORY.
    // Se fileType == DIRECTORY, l'elemento è una cartella — campo 'directory' non necessario
    private FileType fileType;

    // Metadati estesi: EXIF, GPS, durata video, ecc.
    // Popolato solo per foto e video tramite MetadataService
    // Esempio: { "GPS Latitude": "45.4654", "Camera Model": "Canon EOS R5" }
    private Map<String, String> metadata;
}
