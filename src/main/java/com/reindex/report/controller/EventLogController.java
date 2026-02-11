package com.reindex.report.controller;

import com.reindex.report.service.EventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoint per la gestione e l'importazione dei log degli eventi.
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogService eventLogService;

    /**
     * Recupera la lista degli eventi direttamente da Alfresco per un controllo
     * rapido.
     */
    @GetMapping("/alfresco/{nodeId}")
    public ResponseEntity<List<Map<String, Object>>> getAlfrescoEvents(@PathVariable String nodeId) {
        log.info("Richiesta visualizzazione eventi Alfresco per il nodo: {}", nodeId);
        return ResponseEntity.ok(eventLogService.getEventsFromAlfresco(nodeId));
    }

    /**
     * Avvia il processo di importazione e sincronizzazione degli eventi da Alfresco
     * al database persistente.
     */
    @PostMapping("/import/{nodeId}")
    public ResponseEntity<String> importEvents(@PathVariable String nodeId) {
        log.info("Avvio procedura di importazione per il nodo: {}", nodeId);
        try {
            int count = eventLogService.importEventsWithoutDuplicates(nodeId);
            String messaggio = String.format(
                    "Sincronizzazione completata con successo. Processati %d nuovi eventi/aggiornamenti.", count);
            return ResponseEntity.ok(messaggio);
        } catch (Exception e) {
            log.error("Errore critico durante l'importazione per il nodo {}: {}", nodeId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Si è verificato un errore durante l'importazione: " + e.getMessage());
        }
    }
}
