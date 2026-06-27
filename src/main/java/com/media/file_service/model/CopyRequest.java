package com.media.file_service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Corpo della richiesta per copiare file selezionati verso una cartella del NAS.
// Supporta sorgenti diverse: file locali o file da share SMB remota
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CopyRequest {

    // Tipo di sorgente: LOCAL o SMB
    @NotNull(message = "Il tipo di sorgente è obbligatorio (LOCAL o SMB)")
    private SourceType sourceType;

    // Lista dei percorsi dei file da copiare.
    // - Se sourceType = LOCAL: percorsi assoluti sul filesystem del NAS
    // - Se sourceType = SMB: percorsi relativi alla root della share (es. "Foto/2024/foto.jpg")
    @NotEmpty(message = "Seleziona almeno un file da copiare")
    private List<String> sourcePaths;

    // Credenziali SMB — obbligatorie solo se sourceType = SMB, ignorate se LOCAL.
    // @Valid propaga la validazione dei campi interni di SmbConnectionRequest.
    // Il campo 'path' di SmbConnectionRequest non viene usato qui (usiamo sourcePaths)
    @Valid
    private SmbConnectionRequest smbConnection;

    // Cartella di destinazione sul NAS (percorso relativo al basePath configurato in YAML)
    @NotBlank(message = "La cartella di destinazione è obbligatoria")
    private String destinationFolder;
}
