package com.reindex.report.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entità che rappresenta un log di evento persistito su MongoDB.
 * L'utilizzo di Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor)
 * permette di mantenere il codice pulito eliminando il boilerplate tecnico.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "eventLog")
public class EventLog {

    @Id
    private String id;
    private String utente; // Formato: "Nome Cognome (username)"
    private String struttura; // Identificativo della cartella o ufficio
    private LocalDateTime data;
    private String evento; // Descrizione dell'azione (es. "Aggiunto Documento")
    private Map<String, Object> dettagli; // Metadati extra in formato chiave-valore
    private String tipoAudit; // Categoria dell'audit (es. "DOCUMENTO")

    /**
     * Costruttore di utilità senza ID (gestito automaticamente da MongoDB).
     */
    public EventLog(String utente, String struttura, LocalDateTime data, String evento,
            Map<String, Object> dettagli, String tipoAudit) {
        this.utente = utente;
        this.struttura = struttura;
        this.data = data;
        this.evento = evento;
        this.dettagli = dettagli;
        this.tipoAudit = tipoAudit;
    }
}
