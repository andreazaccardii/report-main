package com.reindex.report.service;

import com.reindex.report.dto.FileReportDTO;
import com.reindex.report.entity.EventLog;
import org.alfresco.search.model.ResultNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Componente dedicato alla mappatura dei dati provenienti da Alfresco.
 * Centralizza la logica di parsing delle date e la costruzione di DTO ed
 * Entity,
 * rendendo i servizi più puliti e focalizzati sulla business logic.
 */
@Component
public class AlfrescoMapper {

    private static final DateTimeFormatter ITALIAN_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int EXPIRATION_DAYS = 90;

    /**
     * Converte un nodo Alfresco in una mappa di dati grezzi per uso generico.
     */
    public Map<String, Object> toMap(ResultNode entry) {
        Map<String, Object> data = new HashMap<>();

        // Gestione utente: preferenza a modifiedByUser, fallback su createdByUser
        String userId = "system";
        String userName = "System";
        if (entry.getModifiedByUser() != null) {
            userId = entry.getModifiedByUser().getId();
            userName = entry.getModifiedByUser().getDisplayName();
        } else if (entry.getCreatedByUser() != null) {
            userId = entry.getCreatedByUser().getId();
            userName = entry.getCreatedByUser().getDisplayName();
        }
        data.put("utente", String.format("%s (%s)", userName, userId));

        // Struttura (Parent ID come identificativo cartella)
        data.put("struttura", entry.getParentId() != null ? "Folder_" + entry.getParentId() : "");

        // Data evento
        LocalDateTime eventDate = entry.getModifiedAt() != null ? entry.getModifiedAt().toLocalDateTime()
                : (entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDateTime() : LocalDateTime.now());
        data.put("data", eventDate.toString());

        // Determinazione tipo evento
        String evento = "Aggiunto Documento";
        if (entry.getModifiedAt() != null && entry.getCreatedAt() != null &&
                !entry.getModifiedAt().equals(entry.getCreatedAt())) {
            evento = "Modificato Documento";
        }
        data.put("evento", evento);

        // Dettagli tecnici
        Map<String, Object> dettagli = extractDetails(entry);
        data.put("dettagli", dettagli);
        data.put("tipoAudit", "DOCUMENTO");

        return data;
    }

    /**
     * Converte un nodo Alfresco nell'entità EventLog per MongoDB.
     */
    public EventLog toEntity(ResultNode entry) {
        Map<String, Object> map = toMap(entry);

        return new EventLog(
                (String) map.get("utente"),
                (String) map.get("struttura"),
                LocalDateTime.parse((String) map.get("data")),
                (String) map.get("evento"),
                (Map<String, Object>) map.get("dettagli"),
                (String) map.get("tipoAudit"));
    }

    /**
     * Converte un nodo Alfresco nel DTO per il report.
     */
    public FileReportDTO toReportDTO(ResultNode entry) {
        LocalDateTime creationDate = entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDateTime()
                : LocalDateTime.now();

        LocalDateTime expirationDate = creationDate.plusDays(EXPIRATION_DAYS);
        long daysPassed = ChronoUnit.DAYS.between(creationDate, LocalDateTime.now());

        String nodeType = entry.getNodeType() != null && entry.getNodeType().contains("folder") ? "cartella" : "file";

        return new FileReportDTO(
                entry.getName(),
                nodeType,
                creationDate.format(ITALIAN_FORMATTER),
                expirationDate.format(ITALIAN_FORMATTER),
                ChronoUnit.DAYS.between(creationDate.toLocalDate(), LocalDateTime.now().toLocalDate()));
    }

    /**
     * Estrae i metadati comuni di un documento.
     */
    private Map<String, Object> extractDetails(ResultNode entry) {
        Map<String, Object> dettagli = new HashMap<>();
        dettagli.put("nomeFile", entry.getName());
        dettagli.put("id", entry.getId());
        dettagli.put("stato", "Attivo"); // Stato default

        if (entry.getContent() != null) {
            dettagli.put("mimeType", entry.getContent().getMimeType());
            dettagli.put("dimensione", entry.getContent().getSizeInBytes());
        }

        if (entry.getCreatedAt() != null) {
            LocalDateTime creationDate = entry.getCreatedAt().toLocalDateTime();
            dettagli.put("dataScadenza",
                    creationDate.plusDays(EXPIRATION_DAYS).format(DateTimeFormatter.ISO_DATE_TIME));
            // Calcolo basato sui giorni di calendario (LocalDate) per scattare a mezzanotte
            dettagli.put("giorniTrascorsi",
                    ChronoUnit.DAYS.between(creationDate.toLocalDate(), LocalDateTime.now().toLocalDate()));
        }

        return dettagli;
    }
}
