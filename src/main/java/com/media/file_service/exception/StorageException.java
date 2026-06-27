package com.media.file_service.exception;

// Lanciata per errori di I/O: lettura, scrittura, copia o upload falliti → HTTP 500
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
