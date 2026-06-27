package com.media.file_service.service;

import com.media.file_service.exception.NotFoundException;
import com.media.file_service.exception.StorageException;
import com.media.file_service.model.FileMetadataResponse;
import com.media.file_service.model.FileType;
import com.media.file_service.model.SmbConnectionRequest;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

// Legge file e cartelle da una share SMB/CIFS.
// Le credenziali (host, share, username, password) arrivano dal client ad ogni richiesta:
// non sono più configurate in application.yaml, permettendo di connettersi
// a qualsiasi PC remoto senza modificare la configurazione del server
@Service
@Slf4j
public class SmbFileService {

    // Elenca i file nella cartella SMB indicata nel DTO
    public List<FileMetadataResponse> listFiles(SmbConnectionRequest request) {
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new com.media.file_service.exception.BadRequestException("Il campo 'path' è obbligatorio per l'esplorazione SMB");
        }
        String smbUrl = buildSmbUrl(request);

        // Log senza password — loggare solo host e share per il debug
        log.debug("Connessione SMB a: smb://{}/{}/{}", request.getHost(), request.getShare(), request.getPath());

        try {
            CIFSContext context = buildContext(request);

            try (SmbFile dir = new SmbFile(smbUrl, context)) {
                if (!dir.exists()) {
                    throw new NotFoundException("Percorso SMB non trovato: " + request.getPath());
                }

                SmbFile[] files = dir.listFiles();
                if (files == null) return Collections.emptyList();

                List<FileMetadataResponse> result = new ArrayList<>();
                for (SmbFile file : files) {
                    result.add(buildMetadata(file));
                }
                return result;
            }

        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Errore di connessione SMB: " + e.getMessage(), e);
        }
    }

    // Costruisce il DTO con i metadati del singolo file SMB.
    // Questo metodo non dipende dalle credenziali, riceve solo il SmbFile già aperto
    private FileMetadataResponse buildMetadata(SmbFile file) {
        try {
            boolean isDir = file.isDirectory();
            long size = isDir ? 0 : file.length();
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(file.getLastModified()),
                    ZoneId.systemDefault()
            );
            // SmbFile aggiunge "/" al nome delle cartelle — lo rimuoviamo
            String fileName = file.getName().replace("/", "");
            FileType fileType = isDir ? FileType.DIRECTORY : LocalFileService.detectFileType(fileName);

            return FileMetadataResponse.builder()
                    .fileName(fileName)
                    .fullPath(file.getPath())
                    .size(size)
                    .lastModified(lastModified)
                    .fileType(fileType)
                    // I metadati EXIF su SMB non sono estratti per ora:
                    // richiederebbe scaricare ogni file localmente prima di analizzarlo
                    .metadata(Collections.emptyMap())
                    .build();

        } catch (Exception e) {
            log.warn("Impossibile leggere metadati SMB per {}: {}", file.getName(), e.getMessage());
            return FileMetadataResponse.builder()
                    .fileName(file.getName())
                    .fileType(FileType.OTHER)
                    .build();
        }
    }

    // Costruisce l'URL SMB nel formato: smb://host/share/path/
    private String buildSmbUrl(SmbConnectionRequest request) {
        String path = request.getPath().startsWith("/")
                ? request.getPath().substring(1)
                : request.getPath();
        return String.format("smb://%s/%s/%s/", request.getHost(), request.getShare(), path);
    }

    // Crea il contesto di autenticazione jcifs-ng con le credenziali del DTO.
    // Non logga mai username o password
    private CIFSContext buildContext(SmbConnectionRequest request) throws Exception {
        Properties props = new Properties();
        // Abilita solo SMB2 e SMB3 (SMB1 è disabilitato per motivi di sicurezza)
        props.put("jcifs.smb.client.minVersion", "SMB202");
        props.put("jcifs.smb.client.maxVersion", "SMB311");

        PropertyConfiguration config = new PropertyConfiguration(props);
        BaseContext baseContext = new BaseContext(config);

        String domain = (request.getDomain() != null && !request.getDomain().isBlank())
                ? request.getDomain()
                : "WORKGROUP";

        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                domain, request.getUsername(), request.getPassword()
        );
        return baseContext.withCredentials(auth);
    }
}
