package com.reindex.report.repository;

import com.reindex.report.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Repository per gli eventi - estende JpaRepository per PostgreSQL
@Repository
public interface EventLogRepository extends JpaRepository<EventLog, String> {

    Optional<EventLog> findByUtenteAndDataAndEvento(String utente, LocalDateTime data, String evento);

    boolean existsByUtenteAndDataAndEvento(String utente, LocalDateTime data, String evento);

    // Query Nativa PostgreSQL per cercare dentro il JSONB 'dettagli'
    @Query(value = "SELECT * FROM event_log WHERE dettagli ->> 'id' = :docId", nativeQuery = true)
    List<EventLog> findByDocumentIdOrderByDataDesc(@Param("docId") String docId,
            org.springframework.data.domain.Sort sort);

    // Query Nativa per trovare documenti con tracking (giorniTrascorsi presente)
    @Query(value = "SELECT * FROM event_log WHERE dettagli ->> 'id' = :docId AND dettagli -> 'giorniTrascorsi' IS NOT NULL", nativeQuery = true)
    List<EventLog> findLastWithTrackingData(@Param("docId") String docId, org.springframework.data.domain.Sort sort);
}
