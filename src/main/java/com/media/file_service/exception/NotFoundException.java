package com.media.file_service.exception;

// Lanciata quando un file o percorso richiesto non esiste → HTTP 404
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
