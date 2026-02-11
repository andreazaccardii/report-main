package com.reindex.report.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entità per tracciare lo storico delle sincronizzazioni effettuate.
 * Utilizzata per alimentare le metriche di dashboard come "Ultima esecuzione"
 * e "Documenti attivi".
 */
@Entity
@Table(name = "sync_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_esecuzione", nullable = false)
    private LocalDateTime dataEsecuzione;

    @Column(name = "documenti_attivi")
    private Integer documentiAttivi;

    @Column(name = "nuovi_eventi")
    private Integer nuoviEventi;
}
