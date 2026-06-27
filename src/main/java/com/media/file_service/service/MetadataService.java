package com.media.file_service.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.media.file_service.service.LocalFileService;
import com.media.file_service.model.FileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Orchestratore per l'estrazione dei metadati.
// Decide quale strategy usare in base al tipo di file:
// - Foto (IMAGE) → metadata-extractor (libreria Drew Noakes, lavora in-process)
// - Video/INSV (VIDEO) → ExifTool (processo esterno)
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final ExifToolService exifToolService;

    // Punto d'ingresso: riceve il Path del file e restituisce i metadati come Map
    public Map<String, String> extract(Path filePath) {
        FileType type = LocalFileService.detectFileType(filePath.getFileName().toString());

        return switch (type) {
            case IMAGE -> extractImageMetadata(filePath);
            case VIDEO -> exifToolService.extract(filePath);
            default -> Collections.emptyMap();
        };
    }

    // Tag EXIF utili che vogliamo estrarre, mappati con chiavi leggibili per il client.
    // Formato: "NomeDirectory - NomeTag" (come li produce metadata-extractor) → chiave risposta
    private static final Map<String, String> WANTED_TAGS = Map.of(
            "Exif IFD0 - Model",            "cameraModel",
            "Exif SubIFD - Date/Time Original", "dateTimeOriginal",
            "Exif SubIFD - Image Width",    "imageWidth",
            "Exif SubIFD - Image Height",   "imageHeight",
            "GPS - GPS Latitude",           "gpsLatitude",
            "GPS - GPS Longitude",          "gpsLongitude"
    );

    // Usa la libreria metadata-extractor per leggere i tag EXIF/GPS delle foto.
    // Restituisce solo i campi utili definiti in WANTED_TAGS, ignorando tutto il resto
    private Map<String, String> extractImageMetadata(Path filePath) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(filePath.toFile());
            Map<String, String> result = new LinkedHashMap<>();

            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    // Costruisce la chiave nel formato "NomeDirectory - NomeTag"
                    String key = directory.getName() + " - " + tag.getTagName();
                    // Aggiunge alla risposta solo i tag che ci interessano
                    if (WANTED_TAGS.containsKey(key) && tag.getDescription() != null) {
                        result.put(WANTED_TAGS.get(key), tag.getDescription());
                    }
                }
            }

            return result;

        } catch (Exception e) {
            log.warn("Impossibile estrarre metadati da {}: {}", filePath.getFileName(), e.getMessage());
            return Collections.emptyMap();
        }
    }
}
