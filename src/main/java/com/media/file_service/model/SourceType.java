package com.media.file_service.model;

// Indica da dove provengono i file da copiare
public enum SourceType {

    // File su percorso locale del NAS (USB montata, disco interno, ecc.)
    LOCAL,

    // File su share SMB remota (PC sulla stessa rete)
    SMB
}
