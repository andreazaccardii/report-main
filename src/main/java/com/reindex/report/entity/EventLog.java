package com.reindex.report.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entità che rappresenta un log di evento persistito su PostgreSQL.
 * Modificata per usare JPA invece di Mongo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_log")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String utente; // Formato: "Nome Cognome (username)"
    private String struttura; // Identificativo della cartella o ufficio
    private LocalDateTime data;
    private String evento; // Descrizione dell'azione (es. "Aggiunto Documento")

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> dettagli; // Metadati extra in formato chiave-valore

    private String tipoAudit; // Categoria dell'audit (es. "DOCUMENTO")

    /**
     * Costruttore di utilità senza ID.
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
