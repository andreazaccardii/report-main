package com.reindex.report.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurazione e gestione delle metriche Prometheus per il monitoraggio
 * dell'applicazione.
 * Utilizza Micrometer per esporre dati su esecuzioni, conteggi documenti e
 * statistiche temporali.
 */
@Slf4j
@Component
public class MetricsConfig {

    @Getter
    private final Counter schedulerExecutionCounter;
    private final MeterRegistry registry;

    private double currentMongoEventsCount = 0;
    private double lastSchedulerExecutionTime = 0;
    private double alfrescoActiveDocumentsCount = 0;

    // Collezioni thread-safe per tracciare i conteggi con tag dinamici
    private final Map<String, Integer> mimetypeCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventTypeCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventsByDateCounts = new ConcurrentHashMap<>();

    public MetricsConfig(MeterRegistry registry) {
        this.registry = registry;

        // Contatore globale delle esecuzioni
        this.schedulerExecutionCounter = Counter.builder("scheduler_executions_total")
                .description("Numero totale di esecuzioni dello scheduler di importazione")
                .tag("application", "report")
                .register(registry);

        // Registrazione dei Gauge (misurazioni istantanee)
        Tags commonTags = Tags.of("application", "report");
        registry.gauge("mongodb_events_count", commonTags, this, c -> c.currentMongoEventsCount);
        registry.gauge("alfresco_active_documents_count", commonTags, this, c -> c.alfrescoActiveDocumentsCount);
        registry.gauge("last_scheduler_execution_timestamp", commonTags, this, c -> c.lastSchedulerExecutionTime);
    }

    public void setCurrentMongoEventsCount(double count) {
        this.currentMongoEventsCount = count;
    }

    public void setAlfrescoActiveDocumentsCount(double count) {
        this.alfrescoActiveDocumentsCount = count;
    }

    /**
     * Aggiorna il timestamp dell'ultima esecuzione dello scheduler (in secondi
     * Unix).
     */
    public void setLastSchedulerExecutionTime() {
        this.lastSchedulerExecutionTime = System.currentTimeMillis() / 1000.0;
    }

    /**
     * Aggiorna le metriche relative ai mimetype dei documenti attivi.
     */
    public void updateMimetypeCounts(Map<String, Integer> counts) {
        log.debug("Aggiornamento metriche mimetype: {}", counts);
        mimetypeCounts.clear();
        mimetypeCounts.putAll(counts);

        counts.forEach((mimetype, count) -> Gauge
                .builder("alfresco_documents_by_mimetype", mimetypeCounts,
                        map -> map.getOrDefault(mimetype, 0).doubleValue())
                .description("Numero di documenti suddivisi per categoria mimetype")
                .tag("application", "report")
                .tag("mimetype", mimetype)
                .register(registry));
    }

    /**
     * Aggiorna le metriche relative alla tipologia degli eventi registrati.
     */
    public void updateEventTypeCounts(Map<String, Integer> counts) {
        log.debug("Aggiornamento metriche tipi evento: {}", counts);
        eventTypeCounts.clear();
        eventTypeCounts.putAll(counts);

        counts.forEach((eventType, count) -> Gauge
                .builder("alfresco_documents_by_event_type", eventTypeCounts,
                        map -> map.getOrDefault(eventType, 0).doubleValue())
                .description("Numero di eventi suddivisi per tipologia (es. Aggiunto, Modificato)")
                .tag("application", "report")
                .tag("event_type", eventType)
                .register(registry));
    }

    /**
     * Aggiorna le metriche temporali degli eventi (Serie storica per Grafana).
     */
    public void updateEventsByDateCounts(Map<String, Integer> counts) {
        log.debug("Aggiornamento metriche eventi per data: {}", counts);
        eventsByDateCounts.clear();
        eventsByDateCounts.putAll(counts);

        counts.forEach((compositeKey, count) -> {
            String[] parts = compositeKey.split("\\|");
            String date = parts[0];
            String eventType = parts.length > 1 ? parts[1] : "Unknown";

            Gauge.builder("alfresco_events_by_date", eventsByDateCounts,
                    map -> map.getOrDefault(compositeKey, 0).doubleValue())
                    .description("Volume di eventi giornalieri suddivisi per tipo")
                    .tag("application", "report")
                    .tag("date", date)
                    .tag("event_type", eventType)
                    .register(registry);
        });
    }
}
