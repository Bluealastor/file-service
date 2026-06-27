package com.media.file_service.service;

import com.media.file_service.exception.BadRequestException;
import com.media.file_service.exception.NotFoundException;
import com.media.file_service.model.FileMetadataResponse;
import com.media.file_service.model.FileType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Legge file e cartelle dal filesystem locale del NAS (es. USB montata su /mnt/disks)
@Service
@RequiredArgsConstructor
public class LocalFileService {

    // Percorso base configurato in application.yaml (file.local.base-path).
    // Il client non può navigare fuori da questo percorso per motivi di sicurezza
    @Value("${file.local.base-path}")
    private String basePath;

    private final MetadataService metadataService;

    // Estensioni riconosciute per tipo
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "heic", "heif",
            "raw", "cr2", "cr3", "nef", "arw", "orf", "rw2", "dng"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "m4v", "insv", "lrv", "360"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "ods"
    );

    // Elenca il contenuto di un percorso locale.
    // Il percorso fornito dal client viene risolto relativamente al basePath,
    // impedendo l'accesso a cartelle al di fuori di esso (path traversal)
    public List<FileMetadataResponse> listFiles(String relativePath) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();

        // Path vuoto o null = root: elenca direttamente il basePath
        if (relativePath == null || relativePath.isBlank()) {
            return listDirectory(base);
        }

        // Rifiuta percorsi assoluti: Path.resolve() con argomento assoluto ignora base,
        // vanificando il controllo startsWith che segue
        if (Paths.get(relativePath).isAbsolute()) {
            throw new BadRequestException("Il percorso deve essere relativo, non assoluto");
        }

        // Risolve il percorso relativo rispetto al basePath e lo normalizza
        // (es. rimuove eventuali "../" nel path per sicurezza)
        Path target = base.resolve(relativePath).normalize();

        // Verifica che il percorso richiesto sia dentro il basePath (protezione path traversal)
        if (!target.startsWith(base)) {
            throw new BadRequestException("Accesso negato: percorso fuori dalla cartella consentita");
        }

        if (!Files.exists(target)) {
            throw new NotFoundException("Percorso non trovato: " + relativePath);
        }

        if (!Files.isDirectory(target)) {
            throw new BadRequestException("Il percorso specificato non è una cartella");
        }

        return listDirectory(target);
    }

    // Legge il contenuto di una cartella (non ricorsivo, solo primo livello)
    private List<FileMetadataResponse> listDirectory(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(path -> buildMetadata(path))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new NotFoundException("Impossibile leggere la cartella: " + e.getMessage());
        }
    }

    // Costruisce il DTO FileMetadataResponse a partire da un Path.
    // fullPath è relativo al basePath — il client lo riusa come parametro ?path=
    // per navigare nelle sottocartelle senza passare path assoluti.
    private FileMetadataResponse buildMetadata(Path path) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        // Percorso relativo al basePath (es. "Foto/2024" invece di "/nas-files/Foto/2024")
        String relativePath = base.relativize(path.toAbsolutePath().normalize()).toString();

        try {
            boolean isDir = Files.isDirectory(path);
            long size = isDir ? 0 : Files.size(path);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(),
                    ZoneId.systemDefault()
            );
            FileType fileType = isDir ? FileType.DIRECTORY : detectFileType(path.getFileName().toString());

            FileMetadataResponse.FileMetadataResponseBuilder builder = FileMetadataResponse.builder()
                    .fileName(path.getFileName().toString())
                    .fullPath(relativePath)
                    .size(size)
                    .lastModified(lastModified)
                    .fileType(fileType);

            if (fileType == FileType.IMAGE || fileType == FileType.VIDEO) {
                builder.metadata(metadataService.extract(path));
            }

            return builder.build();

        } catch (IOException e) {
            return FileMetadataResponse.builder()
                    .fileName(path.getFileName().toString())
                    .fullPath(relativePath)
                    .fileType(FileType.OTHER)
                    .build();
        }
    }

    // Determina il tipo del file in base all'estensione
    public static FileType detectFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return FileType.OTHER;

        String ext = fileName.substring(dotIndex + 1).toLowerCase();

        if (IMAGE_EXTENSIONS.contains(ext)) return FileType.IMAGE;
        if (VIDEO_EXTENSIONS.contains(ext)) return FileType.VIDEO;
        if (DOCUMENT_EXTENSIONS.contains(ext)) return FileType.DOCUMENT;
        return FileType.OTHER;
    }
}
