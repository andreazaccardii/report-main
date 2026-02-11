package com.reindex.report.service;

import com.reindex.report.entity.EventLog;
import com.reindex.report.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.search.model.ResultNode;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servizio per la gestione dei log degli eventi.
 * Gestisce l'importazione, la deduplicazione e il monitoraggio dei documenti
 * Alfresco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogService {

    private final AlfrescoService alfrescoService;
    private final EventLogRepository eventLogRepository;
    private final AlfrescoMapper alfrescoMapper;
    private final com.reindex.report.repository.SyncHistoryRepository syncHistoryRepository;

    /**
     * Recupera i documenti da Alfresco e li restituisce come mappa di eventi
     * grezzi.
     */
    public List<Map<String, Object>> getEventsFromAlfresco(String nodeId) {
        log.info("Recupero eventi aggiornati da Alfresco per il nodo: {}", nodeId);

        return alfrescoService.searchDocuments(nodeId).stream()
                .map(alfrescoMapper::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Esegue l'importazione degli eventi evitando duplicati e gestendo le
     * eliminazioni e i cambi giorno.
     */
    public int importEventsWithoutDuplicates(String nodeId) {
        log.info("Inizio processo di sincronizzazione eventi per il nodo: {}", nodeId);

        List<ResultNode> alfrescoNodes = alfrescoService.searchDocuments(nodeId);
        List<String> currentDocumentIds = alfrescoNodes.stream()
                .map(ResultNode::getId)
                .collect(Collectors.toList());

        List<String> savedDocumentIds = getSavedDocumentIds();

        // 1. Gestione dei documenti eliminati
        List<String> deletedDocumentIds = savedDocumentIds.stream()
                .filter(id -> !currentDocumentIds.contains(id))
                .collect(Collectors.toList());

        int deletionEvents = createDeletionEvents(deletedDocumentIds);

        // 2. Processamento documenti attivi (nuovi o modificati)
        if (alfrescoNodes.isEmpty()) {
            return deletionEvents;
        }

        List<EventLog> eventsToSave = alfrescoNodes.stream()
                .map(alfrescoMapper::toEntity)
                .peek(event -> {
                    // Puliamo i campi di tracking SOLO per le modifiche, NON per i nuovi documenti
                    if ("Modificato Documento".equals(event.getEvento())) {
                        event.getDettagli().remove("dataScadenza");
                        event.getDettagli().remove("giorniTrascorsi");
                    }
                    // "Aggiunto Documento" mantiene sempre giorniTrascorsi per il tracking
                    // temporale
                })
                .collect(Collectors.toList());

        // Filtriamo solo gli eventi che non abbiamo ancora nel database
        List<EventLog> newEvents = eventsToSave.stream()
                .filter(event -> !eventLogRepository.existsByUtenteAndDataAndEvento(
                        event.getUtente(), event.getData(), event.getEvento()))
                .collect(Collectors.toList());

        int savedCount = 0;
        if (!newEvents.isEmpty()) {
            eventLogRepository.saveAll(newEvents);
            savedCount = newEvents.size();
            log.info("Sincronizzazione completata: salvati {} nuovi eventi da Alfresco", savedCount);
        }

        // 3. Controllo passaggio dei giorni (eventi sintetici)
        int dayChangeEvents = checkForDayChanges(alfrescoNodes);

        int totalNewEvents = savedCount + deletionEvents + dayChangeEvents;

        // 4. Salvataggio storico sincronizzazione per metriche
        try {
            com.reindex.report.entity.SyncHistory history = com.reindex.report.entity.SyncHistory.builder()
                    .dataEsecuzione(LocalDateTime.now())
                    .documentiAttivi(alfrescoNodes.size())
                    .nuoviEventi(totalNewEvents)
                    .build();
            syncHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Errore durante il salvataggio dello storico sincronizzazione", e);
        }

        return totalNewEvents;
    }

    /**
     * Verifica se il contatore 'giorniTrascorsi' è aumentato per i documenti
     * attivi.
     * Gestisce sia documenti con storico che documenti nuovi senza eventi
     * precedenti.
     */
    private int checkForDayChanges(List<ResultNode> alfrescoNodes) {
        List<EventLog> updatesToSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (ResultNode node : alfrescoNodes) {
            String docId = node.getId();

            // Recuperiamo l'ultimo evento che conteneva i dati di tracking
            List<EventLog> savedEvents = eventLogRepository.findLastWithTrackingData(docId,
                    Sort.by(Sort.Direction.DESC, "data"));

            EventLog currentData = alfrescoMapper.toEntity(node);
            long currentDays = ((Number) currentData.getDettagli().get("giorniTrascorsi")).longValue();

            if (!savedEvents.isEmpty()) {
                // CASO 1: Documento con storico - aggiorna solo se i giorni sono aumentati
                EventLog lastSaved = savedEvents.get(0);

                if (lastSaved.getDettagli() != null && lastSaved.getDettagli().containsKey("giorniTrascorsi")) {
                    long savedDays = ((Number) lastSaved.getDettagli().get("giorniTrascorsi")).longValue();

                    if (currentDays > savedDays) {
                        log.info("Cambio giorno rilevato per {}: recupero {} giorni mancanti.", node.getName(),
                                (currentDays - savedDays));

                        for (long d = savedDays + 1; d <= currentDays; d++) {
                            long diff = currentDays - d;
                            LocalDateTime midnight = now.toLocalDate().minusDays(diff).atStartOfDay();

                            EventLog historicalEvent = alfrescoMapper.toEntity(node);
                            historicalEvent.setData(midnight);
                            historicalEvent.setEvento("Aggiornamento Statistiche");
                            historicalEvent.getDettagli().put("giorniTrascorsi", d);

                            updatesToSave.add(historicalEvent);
                        }
                    }
                }
            } else {
                // CASO 2: Documento senza storico di tracking - crea evento iniziale se
                // necessario
                // Questo gestisce documenti nuovi o documenti i cui ultimi eventi erano
                // "Modificato Documento"
                if (currentDays > 0) {
                    // Verifica se esiste già un evento "Aggiornamento Statistiche" per oggi
                    LocalDateTime todayMidnight = now.toLocalDate().atStartOfDay();
                    boolean alreadyHasTodayUpdate = eventLogRepository.existsByUtenteAndDataAndEvento(
                            currentData.getUtente(), todayMidnight, "Aggiornamento Statistiche");

                    if (!alreadyHasTodayUpdate) {
                        log.info(
                                "Documento senza storico tracking rilevato: {}, creazione evento iniziale con {} giorni.",
                                node.getName(), currentDays);

                        EventLog historicalEvent = alfrescoMapper.toEntity(node);
                        historicalEvent.setData(todayMidnight);
                        historicalEvent.setEvento("Aggiornamento Statistiche");
                        historicalEvent.getDettagli().put("giorniTrascorsi", currentDays);

                        updatesToSave.add(historicalEvent);
                    }
                }
            }
        }

        if (!updatesToSave.isEmpty()) {
            eventLogRepository.saveAll(updatesToSave);
            log.info("Completato aggiornamento statistico: inseriti {} eventi di cambio giorno.", updatesToSave.size());
        }

        return updatesToSave.size();
    }

    private List<String> getSavedDocumentIds() {
        return eventLogRepository.findAll().stream()
                .filter(e -> e.getDettagli() != null && e.getDettagli().containsKey("id"))
                .map(e -> (String) e.getDettagli().get("id"))
                .distinct()
                .collect(Collectors.toList());
    }

    private int createDeletionEvents(List<String> deletedDocumentIds) {
        int createdEvents = 0;

        for (String docId : deletedDocumentIds) {
            List<EventLog> docEvents = eventLogRepository.findByDocumentIdOrderByDataDesc(docId,
                    Sort.by(Sort.Direction.DESC, "data"));

            if (!docEvents.isEmpty()) {
                EventLog lastEvent = docEvents.get(0);

                // Verifichiamo se non l'abbiamo già segnato come eliminato
                if (!"Eliminato Documento".equals(lastEvent.getEvento())) {
                    Map<String, Object> deletionDetails = new HashMap<>(lastEvent.getDettagli());
                    deletionDetails.put("stato", "Eliminato");
                    deletionDetails.put("dataEliminazione",
                            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                    // Puliamo i campi di tracking per l'evento di eliminazione
                    deletionDetails.remove("dataScadenza");
                    deletionDetails.remove("giorniTrascorsi");

                    EventLog deletionEvent = new EventLog(
                            "System (system)",
                            lastEvent.getStruttura(),
                            LocalDateTime.now(),
                            "Eliminato Documento",
                            deletionDetails,
                            "DOCUMENTO");

                    eventLogRepository.save(deletionEvent);
                    createdEvents++;
                    log.info("Documento {} non più presente su Alfresco: creato evento di eliminazione.", docId);
                }
            }
        }
        return createdEvents;
    }
}
