package com.media.file_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Chiama ExifTool (eseguibile esterno) per estrarre metadati da video e file .insv.
// ExifTool deve essere installato sul sistema (o nel container Docker su Unraid).
// Comando eseguito: exiftool -json -n <percorso-file>
@Service
@Slf4j
public class ExifToolService {

    // Percorso dell'eseguibile ExifTool, configurato in application.yaml (exiftool.path)
    // Default: "exiftool" (deve essere nel PATH di sistema)
    @Value("${exiftool.path}")
    private String exiftoolPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Tag ExifTool utili per i video, mappati con chiavi leggibili per il client.
    // ExifTool con -json restituisce i tag come chiavi piatte (senza prefisso directory)
    private static final Map<String, String> WANTED_TAGS = Map.of(
            "Model",          "cameraModel",
            "CreateDate",     "dateTimeOriginal",
            "ImageWidth",     "imageWidth",
            "ImageHeight",    "imageHeight",
            "GPSLatitude",    "gpsLatitude",
            "GPSLongitude",   "gpsLongitude",
            "Duration",       "duration",
            "VideoFrameRate", "frameRate"
    );

    // Estrae i metadati dal file usando ExifTool e li restituisce come Map<String, String>
    public Map<String, String> extract(Path filePath) {
        try {
            // Lancia ExifTool come processo esterno con output in formato JSON
            // -json: output strutturato
            // -n: valori numerici (coordinate GPS come numeri, non gradi/minuti/secondi)
            ProcessBuilder processBuilder = new ProcessBuilder(
                    exiftoolPath, "-json", "-n", filePath.toString()
            );
            // Reindirizza stderr su stdout per catturare eventuali errori di ExifTool
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // ExifTool restituisce un array JSON con un oggetto per file: [{ "key": "value", ... }]
            // Leggiamo il primo (e unico) elemento dell'array
            List<Map<String, Object>> results = objectMapper.readValue(
                    process.getInputStream(),
                    // l'inferenza del tipo (il diamond operator <>)
                    // Java capisce automaticamente che il tipo generico
                    // dovrebbe essere List<Map<String, Object>>
                    // perché lo si assegna a una variabile dichiarata con quel tipo esplicito
                    //e vita il cancellamento dei dati in runtime
                    // TypeReference con la sottoclasse anonima OBJECT
                    new TypeReference<>() {}
            );

            process.waitFor();

            if (results == null || results.isEmpty()) {
                return Collections.emptyMap();
            }

            // Filtra solo i tag utili definiti in WANTED_TAGS, ignorando tutto il resto
            Map<String, Object> raw = results.get(0);
            Map<String, String> metadata = new java.util.LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (value != null && WANTED_TAGS.containsKey(key)) {
                    metadata.put(WANTED_TAGS.get(key), value.toString());
                }
            });

            return metadata;

        } catch (IOException | InterruptedException e) {
            // Se ExifTool non è installato o fallisce, logghiamo l'errore
            // ma non blocchiamo la risposta: restituiamo metadati vuoti
            log.warn("ExifTool non disponibile o errore per {}: {}", filePath.getFileName(), e.getMessage());
            return Collections.emptyMap();
        }
    }
}
