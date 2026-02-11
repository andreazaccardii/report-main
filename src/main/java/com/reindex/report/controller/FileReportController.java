package com.reindex.report.controller;

import com.reindex.report.dto.FileReportDTO;
import com.reindex.report.service.FileReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller per la generazione dei report sui file presenti in Alfresco.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class FileReportController {

    private final FileReportService fileReportService;

    /**
     * Endpoint che restituisce un report dettagliato dei file contenuti in una
     * cartella specifica.
     * Include calcoli sulla scadenza e sui giorni trascorsi.
     */
    @GetMapping("/{nodeId}")
    public ResponseEntity<List<FileReportDTO>> getFileReport(@PathVariable String nodeId) {
        log.info("Richiesta generazione report per il nodo Alfresco: {}", nodeId);

        List<FileReportDTO> reports = fileReportService.getReports(nodeId);
        log.info("Report generato con successo: {} elementi trovati.", reports.size());

        return ResponseEntity.ok(reports);
    }
}
