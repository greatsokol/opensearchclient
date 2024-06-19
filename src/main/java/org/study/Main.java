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
        List<Document> documents = Arrays.asList(
                Document.builder().date("2014-11-06").name("Spring eXchange 2014 - London")
                        .keywords(Arrays.asList("java", "spring")).build(),
                Document.builder().date("2014-11-06").name("Spring eXchange 2014 - London")
                        .keywords(Arrays.asList("java", "spring")).build()
        );

        documentCustomRepository.init();
        documentCustomRepository.saveAll(documents);
    }
}