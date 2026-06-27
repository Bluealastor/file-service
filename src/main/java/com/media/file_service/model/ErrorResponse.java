package com.media.file_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Corpo standard delle risposte di errore HTTP.
// Restituito dal GlobalExceptionHandler per tutti gli errori dell'applicazione
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}
