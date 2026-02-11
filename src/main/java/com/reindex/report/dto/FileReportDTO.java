package com.reindex.report.dto;

import lombok.Value;

/**
 * Data Transfer Object per i report dei file.
 * L'utilizzo di @Value di Lombok lo rende immutabile (tutti i campi final),
 * ideale per trasportare i dati verso il frontend in modo sicuro e pulito.
 */
@Value
public class FileReportDTO {
    String nomeFile;
    String tipoNodo;
    String dataCreazione;
    String dataDiScadenza;
    long giorniTrascorsi;
}
