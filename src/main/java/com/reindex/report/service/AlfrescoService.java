package com.reindex.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.RequestPagination;
import org.alfresco.search.model.RequestQuery;
import org.alfresco.search.model.ResultSetPaging;
import org.alfresco.search.model.ResultNode;
import org.alfresco.search.model.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servizio dedicato alla comunicazione con le API di ricerca di Alfresco.
 * Centralizza la costruzione delle query e la gestione del paging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlfrescoService {

    private final SearchApi searchApi;

    /**
     * Esegue una ricerca di tutti i documenti contenuti ricorsivamente in un nodo.
     */
    public List<ResultNode> searchDocuments(String nodeId) {
        log.info("Avvio ricerca documenti in Alfresco per il nodo: {}", nodeId);

        RequestQuery requestQuery = new RequestQuery();
        String parentReference = "workspace://SpacesStore/" + nodeId;
        // Cerchiamo tutti i contenuti (cm:content) che hanno come antenato il nodo
        // specificato
        requestQuery.setQuery("+ANCESTOR:\"" + parentReference + "\" AND +TYPE:\"cm:content\"");

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery(requestQuery);
        // Impostiamo un limite alto per il recupero dei documenti (standard per
        // export/report)
        searchRequest.setPaging(new RequestPagination().maxItems(1000).skipCount(0));

        try {
            ResultSetPaging response = searchApi.search(searchRequest).getBody();
            List<ResultNode> results = new ArrayList<>();

            if (response != null && response.getList() != null) {
                response.getList().getEntries().forEach(item -> results.add(item.getEntry()));
            }

            log.info("Ricerca completata: trovati {} documenti per il nodo {}", results.size(), nodeId);
            return results;
        } catch (Exception e) {
            log.error("Errore durante la ricerca su Alfresco per il nodo {}: {}", nodeId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
