package com.reindex.report.repository;

import com.reindex.report.entity.SyncHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository per l'accesso ai dati della tabella sync_history.
 */
@Repository
public interface SyncHistoryRepository extends JpaRepository<SyncHistory, Long> {
}
