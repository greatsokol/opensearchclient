package org.study.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.study.client.OpenSeaarchRestClient;
import org.study.domain.Document;

@Component
public class DocumentCustomRepository {
    private final String indexName = "someindex3";
    @Autowired
    OpenSeaarchRestClient<Document> osClient;

    public void init() {
        osClient.createIndex(indexName);
    }

    public void save(Document document) {
        osClient.insertDocument(indexName, document);
    }

    public void saveAll(Iterable<Document> entities) {
        entities.forEach(this::save);
    }

}
