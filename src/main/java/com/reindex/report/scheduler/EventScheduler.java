package com.reindex.report.scheduler;

import com.reindex.report.service.EventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler incaricato di eseguire la sincronizzazione periodica con Alfresco.
 * Logica delle metriche rimossa (ora gestita direttamente da Grafana su
 * PostgreSQL).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventScheduler {

    private final EventLogService eventLogService;

    @Value("${scheduler.node-id}")
    private String nodeId;

    /**
     * Esecuzione periodica ogni 10 secondi.
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledImportEvents() {
        if (isNodeIdInvalid()) {
            log.warn("Scheduler: 'scheduler.node-id' non configurato o non valido.");
            return;
        }

        try {
            int count = eventLogService.importEventsWithoutDuplicates(nodeId);
            if (count > 0) {
                log.info("Sincronizzazione automatica eseguita: trovati e processati {} nuovi eventi per il nodo {}.",
                        count, nodeId);
            }
        } catch (Exception e) {
            log.error("Errore durante l'esecuzione dello scheduler di importazione", e);
        }
    }

    private boolean isNodeIdInvalid() {
        return nodeId == null || nodeId.isEmpty() || "INSERISCI_QUI_IL_TUO_NODE_ID".equals(nodeId);
    }
}
