package org.study;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.study.domain.Document;
import org.study.repository.DocumentCustomRepository;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class Main {
    @Autowired
    DocumentCustomRepository documentCustomRepository;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @PostConstruct
    public void test() {
        Document oneDoument = Document.builder()
                .date("2024-06-19")
                .name("Only one document")
                .keywords(Arrays.asList("java", "spring"))
                .build();

        List<Document> documents = Arrays.asList(
                Document.builder().date("2024-06-20").name("Bulk operation document #1")
                        .keywords(Arrays.asList("bssscript", "dictman", "cbank", "compiler", "bss")).build(),
                Document.builder().date("2024-06-20").name("Bulk operation document #2")
                        .keywords(Arrays.asList("java", "spring", "psb")).build()
        );

        documentCustomRepository.init();
        documentCustomRepository.save(oneDoument);
        documentCustomRepository.saveAll(documents);
    }
}