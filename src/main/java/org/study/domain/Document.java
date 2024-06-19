package org.study.domain;


import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder

public class Document {
    private String name;
    private String date;
    private List<String> keywords;
}
