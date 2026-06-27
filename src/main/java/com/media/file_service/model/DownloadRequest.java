package com.media.file_service.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Corpo della richiesta POST per il download di un file.
// Usa POST invece di GET perché il path non deve finire nell'URL:
// gli URL compaiono nei log di server e proxy, nella history del browser
// e possono essere condivisi accidentalmente.
// Con POST il path viaggia nel body, invisibile ai log intermedi
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownloadRequest {

    // Percorso relativo al basePath del file da scaricare (es. "Foto/2024/foto.jpg")
    @NotBlank(message = "Il percorso del file è obbligatorio")
    private String path;
}
