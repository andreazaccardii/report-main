package com.reindex.report.repository;

import com.reindex.report.entity.EventLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repository per gli eventi - estende MongoRepository per CRUD base
@Repository
public interface EventLogRepository extends MongoRepository<EventLog, String> {

    // Metodo custom per trovare eventi specifici (utente+data+evento)
    Optional<EventLog> findByUtenteAndDataAndEvento(String utente, java.time.LocalDateTime data, String evento);

    // Metodo per controllare se esiste già un evento (serve per evitare duplicati)
    boolean existsByUtenteAndDataAndEvento(String utente, java.time.LocalDateTime data, String evento);

    // Trova tutti gli eventi per un ID documento specifico (annidato in dettagli)
    // ordinati per data decrescente
    @org.springframework.data.mongodb.repository.Query("{'dettagli.id': ?0}")
    java.util.List<EventLog> findByDocumentIdOrderByDataDesc(String docId, org.springframework.data.domain.Sort sort);

    // Trova l'ultimo evento per un documento che contenga ancora i dati di tracking
    // (giorniTrascorsi)
    @org.springframework.data.mongodb.repository.Query("{'dettagli.id': ?0, 'dettagli.giorniTrascorsi': { $exists: true }}")
    java.util.List<EventLog> findLastWithTrackingData(String docId, org.springframework.data.domain.Sort sort);
}
