package com.media.file_service.exception;

// Lanciata per richieste non valide (es. path null, destinazione mancante) → HTTP 400
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
