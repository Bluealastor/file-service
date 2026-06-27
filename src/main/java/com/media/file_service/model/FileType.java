package com.media.file_service.model;

// Tipo del file, usato per decidere quale extractor di metadati utilizzare
public enum FileType {
    IMAGE,    // JPG, PNG, RAW, HEIC, ecc. → metadata-extractor
    VIDEO,    // MP4, MOV, INSV, ecc. → ExifTool
    DOCUMENT, // PDF, DOCX, XLSX, ecc.
    OTHER,    // Tutto il resto
    DIRECTORY // Cartella
}
