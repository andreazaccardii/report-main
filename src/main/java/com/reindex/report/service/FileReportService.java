package com.reindex.report.service;

import com.reindex.report.dto.FileReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servizio per la generazione dei report sui file.
 * Si occupa di recuperare i dati da Alfresco e trasformarli in DTO pronti per
 * la visualizzazione.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileReportService {

    private final AlfrescoService alfrescoService;
    private final AlfrescoMapper alfrescoMapper;

    /**
     * Genera un report dei file contenuti nel nodo specificato.
     */
    public List<FileReportDTO> getReports(String nodeId) {
        log.info("Generazione report richiesta per il nodo: {}", nodeId);

        return alfrescoService.searchDocuments(nodeId).stream()
                .map(alfrescoMapper::toReportDTO)
                .collect(Collectors.toList());
    }
}
