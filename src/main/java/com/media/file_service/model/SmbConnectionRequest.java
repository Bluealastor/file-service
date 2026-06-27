package com.media.file_service.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO con le credenziali e il percorso per connettersi a una share SMB.
// Inviato dal client nel body di ogni richiesta POST — mai in query string,
// perché gli URL finiscono nei log di server e proxy, esponendo la password
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SmbConnectionRequest {

    // IP o hostname del PC remoto (es. "192.168.1.100" o "PC-SALA")
    @NotBlank(message = "Host obbligatorio")
    private String host;

    // Nome della cartella condivisa su Windows/Linux (es. "Condivisione", "Media")
    @NotBlank(message = "Share obbligatoria")
    private String share;

    // Percorso relativo dentro la share da esplorare (es. "Foto/2024").
    // Obbligatorio per GET /api/files/smb, ignorato quando usato dentro CopyRequest
    private String path;

    @NotBlank(message = "Username obbligatorio")
    private String username;

    // La password non viene mai loggata — verificare che nessun log.debug() stampi il DTO
    @NotBlank(message = "Password obbligatoria")
    private String password;

    // Dominio Windows (opzionale, spesso vuoto in reti domestiche — usa "WORKGROUP" come default)
    private String domain = "WORKGROUP";
}
