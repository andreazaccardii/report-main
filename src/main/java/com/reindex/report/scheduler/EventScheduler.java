package com.reindex.report.scheduler;

import com.reindex.report.config.MetricsConfig;
import com.reindex.report.entity.EventLog;
import com.reindex.report.repository.EventLogRepository;
import com.reindex.report.service.EventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler incaricato di eseguire la sincronizzazione periodica con Alfresco
 * e l'aggiornamento delle metriche per il monitoraggio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventScheduler {

    private final EventLogService eventLogService;
    private final MetricsConfig metricsConfig;
    private final EventLogRepository eventLogRepository;

    @Value("${scheduler.node-id}")
    private String nodeId;

    /**
     * Esecuzione periodica ogni 10 secondi.
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledImportEvents() {
        metricsConfig.getSchedulerExecutionCounter().increment();
        metricsConfig.setLastSchedulerExecutionTime();

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

            updateMetrics();
        } catch (Exception e) {
            log.error("Errore durante l'esecuzione dello scheduler di importazione", e);
        }
    }

    private boolean isNodeIdInvalid() {
        return nodeId == null || nodeId.isEmpty() || "INSERISCI_QUI_IL_TUO_NODE_ID".equals(nodeId);
    }

    /**
     * Ricalcola le metriche visualizzate su Grafana/Prometheus.
     */
    private void updateMetrics() {
        try {
            // Statistiche MongoDB
            long mongoCount = eventLogRepository.count();
            metricsConfig.setCurrentMongoEventsCount(mongoCount);

            if (nodeId != null && !nodeId.isEmpty()) {
                List<Map<String, Object>> alfrescoDocuments = eventLogService.getEventsFromAlfresco(nodeId);
                metricsConfig.setAlfrescoActiveDocumentsCount(alfrescoDocuments.size());

                // Metriche per mimetype (categorizzato per una visualizzazione pulita)
                Map<String, Integer> mimetypeCounts = calculateMimetypeCounts(alfrescoDocuments);
                metricsConfig.updateMimetypeCounts(mimetypeCounts);
            }

            // Metriche storiche basate sugli eventi salvati
            metricsConfig.updateEventTypeCounts(calculateEventTypeCounts());
            metricsConfig.updateEventsByDateCounts(calculateEventsByDateCounts());

        } catch (Exception e) {
            log.error("Errore durante l'aggiornamento delle metriche", e);
        }
    }

    private Map<String, Integer> calculateMimetypeCounts(List<Map<String, Object>> documents) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map<String, Object> doc : documents) {
            Object dettagliObj = doc.get("dettagli");
            if (dettagliObj instanceof Map<?, ?> dettagli) {
                Object mimeObj = dettagli.get("mimeType");
                if (mimeObj instanceof String rawMime) {
                    String category = classifyMimetype(rawMime);
                    counts.merge(category, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private String classifyMimetype(String rawMime) {
        if (rawMime == null)
            return "Unknown";

        String mime = rawMime.toLowerCase();
        if (mime.contains("pdf"))
            return "PDF";
        if (mime.startsWith("image/"))
            return "Immagini";
        if (mime.startsWith("text/"))
            return "Testo";
        if (mime.contains("word") || mime.contains("officedocument.wordprocessingml"))
            return "Documenti Word";
        if (mime.contains("excel") || mime.contains("officedocument.spreadsheetml"))
            return "Fogli Excel";
        if (mime.contains("powerpoint") || mime.contains("officedocument.presentationml"))
            return "Presentazioni";

        return "Altro";
    }

    private Map<String, Integer> calculateEventTypeCounts() {
        Map<String, Integer> counts = new HashMap<>();
        List<EventLog> events = eventLogRepository.findAll();

        for (EventLog event : events) {
            String evento = event.getEvento();
            if (evento != null && !evento.isEmpty()) {
                counts.merge(evento, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, Integer> calculateEventsByDateCounts() {
        Map<String, Integer> counts = new HashMap<>();
        List<EventLog> events = eventLogRepository.findAll();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        for (EventLog event : events) {
            String evento = event.getEvento();
            if (event.getData() != null && evento != null && !evento.isEmpty()) {
                String dateKey = event.getData().format(formatter);
                String compositeKey = dateKey + "|" + evento;
                counts.merge(compositeKey, 1, Integer::sum);
            }
        }
        return counts;
    }
}
