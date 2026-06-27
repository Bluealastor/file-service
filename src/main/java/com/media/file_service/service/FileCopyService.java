package com.media.file_service.service;

import com.media.file_service.exception.BadRequestException;
import com.media.file_service.exception.NotFoundException;
import com.media.file_service.exception.StorageException;
import com.media.file_service.model.CopyRequest;
import com.media.file_service.model.SourceType;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Gestisce copia di file verso il NAS da sorgenti diverse (locale o SMB),
// upload dal browser e download verso il client
@Service
@Slf4j
public class FileCopyService {

    @Value("${file.local.base-path}")
    private String basePath;

    @Value("${file.upload.destination}")
    private String uploadDestination;

    // Copia i file selezionati nella cartella di destinazione.
    // Delega alla strategia corretta in base a sourceType (LOCAL o SMB)
    public List<String> copyFiles(CopyRequest request) {
        // Verifica che le credenziali SMB siano presenti se la sorgente è SMB
        if (request.getSourceType() == SourceType.SMB && request.getSmbConnection() == null) {
            throw new BadRequestException("Le credenziali SMB sono obbligatorie per sourceType = SMB");
        }

        Path base = Paths.get(basePath).toAbsolutePath().normalize();

        if (Paths.get(request.getDestinationFolder()).isAbsolute()) {
            throw new BadRequestException("La cartella di destinazione deve essere un percorso relativo, non assoluto");
        }

        Path destination = base.resolve(request.getDestinationFolder()).normalize();

        // Sicurezza: la destinazione deve essere dentro il basePath
        if (!destination.startsWith(base)) {
            throw new BadRequestException("Destinazione fuori dalla cartella consentita");
        }

        try {
            Files.createDirectories(destination);
        } catch (IOException e) {
            throw new StorageException("Impossibile creare la cartella di destinazione", e);
        }

        return switch (request.getSourceType()) {
            case LOCAL -> copyFromLocal(request.getSourcePaths(), destination);
            case SMB   -> copyFromSmb(request, destination);
        };
    }

    // Copia file da percorsi locali del NAS.
    // Ogni sorgente viene verificata contro basePath per prevenire path traversal:
    // un client non può copiare file di sistema esterni alla cartella consentita
    private List<String> copyFromLocal(List<String> sourcePaths, Path destination) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        List<String> copied = new ArrayList<>();

        for (String sourcePath : sourcePaths) {
            // Rifiuta percorsi assoluti
            if (Paths.get(sourcePath).isAbsolute()) {
                throw new BadRequestException("Il percorso '" + sourcePath + "' deve essere relativo, non assoluto");
            }

            // Risolve il percorso relativo contro base e normalizza (rimuove "..", ".", ecc.)
            Path source = base.resolve(sourcePath).normalize();

            // Se il percorso risultante esce dal basePath, blocca la richiesta
            if (!source.startsWith(base)) {
                throw new BadRequestException(
                        "Accesso negato: il percorso '" + sourcePath + "' è fuori dalla cartella consentita"
                );
            }

            if (!Files.exists(source)) {
                log.warn("File locale non trovato, salto: {}", sourcePath);
                continue;
            }

            try {
                Path target = destination.resolve(source.getFileName());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                copied.add(target.toString());
                log.info("Copiato (locale): {} → {}", source.getFileName(), destination);
            } catch (IOException e) {
                throw new StorageException("Errore durante la copia di " + sourcePath, e);
            }
        }

        return copied;
    }

    // Copia file da una share SMB remota verso il NAS locale.
    // Apre lo stream del file SMB e lo scrive direttamente sul disco locale
    private List<String> copyFromSmb(CopyRequest request, Path destination) {
        List<String> copied = new ArrayList<>();

        try {
            CIFSContext context = buildSmbContext(request);

            for (String remotePath : request.getSourcePaths()) {
                // Costruisce l'URL SMB per il singolo file
                String smbUrl = buildSmbFileUrl(request, remotePath);

                try (SmbFile smbFile = new SmbFile(smbUrl, context)) {
                    if (!smbFile.exists()) {
                        log.warn("File SMB non trovato, salto: {}", remotePath);
                        continue;
                    }

                    // Estrae solo il nome del file dal percorso remoto
                    String fileName = Paths.get(remotePath).getFileName().toString();
                    Path target = destination.resolve(fileName);

                    // Copia lo stream SMB direttamente sul file locale
                    try (InputStream in = smbFile.getInputStream()) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }

                    copied.add(target.toString());
                    log.info("Copiato (SMB): {} → {}", fileName, destination);

                } catch (IOException e) {
                    throw new StorageException("Errore durante la copia SMB di " + remotePath, e);
                }
            }

        } catch (StorageException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Errore di connessione SMB durante la copia", e);
        }

        return copied;
    }

    // Costruisce l'URL SMB per un singolo file: smb://host/share/percorso/file.jpg
    private String buildSmbFileUrl(CopyRequest request, String remotePath) {
        String path = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
        return String.format("smb://%s/%s/%s",
                request.getSmbConnection().getHost(),
                request.getSmbConnection().getShare(),
                path);
    }

    // Crea il contesto di autenticazione jcifs-ng — stesso approccio di SmbFileService
    private CIFSContext buildSmbContext(CopyRequest request) throws Exception {
        Properties props = new Properties();
        props.put("jcifs.smb.client.minVersion", "SMB202");
        props.put("jcifs.smb.client.maxVersion", "SMB311");

        PropertyConfiguration config = new PropertyConfiguration(props);
        BaseContext baseContext = new BaseContext(config);

        String domain = request.getSmbConnection().getDomain();
        if (domain == null || domain.isBlank()) domain = "WORKGROUP";

        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                domain,
                request.getSmbConnection().getUsername(),
                request.getSmbConnection().getPassword()
        );
        return baseContext.withCredentials(auth);
    }

    // Riceve un file dal browser (multipart/form-data) e lo salva nella cartella di upload
    public String uploadFile(MultipartFile file, String subFolder) {
        if (file.isEmpty()) {
            throw new BadRequestException("Il file è vuoto");
        }

        if (subFolder != null && !subFolder.isBlank() && Paths.get(subFolder).isAbsolute()) {
            throw new BadRequestException("La sottocartella deve essere un percorso relativo, non assoluto");
        }

        Path uploadBase = Paths.get(uploadDestination).toAbsolutePath().normalize();
        Path destination = subFolder != null && !subFolder.isBlank()
                ? uploadBase.resolve(subFolder).normalize()
                : uploadBase;

        if (!destination.startsWith(uploadBase)) {
            throw new BadRequestException("La sottocartella è fuori dalla cartella di upload consentita");
        }

        try {
            Files.createDirectories(destination);
            Path targetPath = destination.resolve(
                    Paths.get(file.getOriginalFilename()).getFileName().toString()
            );
            file.transferTo(targetPath);
            log.info("Upload completato: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            throw new StorageException("Errore durante l'upload del file", e);
        }
    }

    // Restituisce il Path del file richiesto per il download, con verifica di sicurezza.
    // Accetta solo percorsi relativi: se il client manda un percorso assoluto
    // (es. "/etc/passwd"), viene rifiutato prima ancora di toccare il filesystem.
    // Questo è necessario perché Path.resolve() con un argomento assoluto ignora
    // completamente base e ritorna l'argomento stesso — vanificando la protezione
    public Path resolveDownloadPath(String filePath) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();

        // Rifiuta percorsi assoluti — il client deve sempre usare percorsi relativi al basePath
        if (Paths.get(filePath).isAbsolute()) {
            throw new BadRequestException("Il percorso deve essere relativo, non assoluto");
        }

        Path target = base.resolve(filePath).normalize();

        if (!target.startsWith(base)) {
            throw new BadRequestException("Accesso negato: percorso fuori dalla cartella consentita");
        }
        if (!Files.exists(target)) {
            throw new NotFoundException("File non trovato: " + filePath);
        }
        if (Files.isDirectory(target)) {
            throw new BadRequestException("Non è possibile scaricare una cartella");
        }

        return target;
    }
}
