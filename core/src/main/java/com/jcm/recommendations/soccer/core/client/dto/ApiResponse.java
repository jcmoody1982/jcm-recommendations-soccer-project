package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> {

    private boolean success;
    private Pager pager;
    private List<T> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pager {
        private int currentPage;
        private int maxPage;
        private int resultsPerPage;
        private int totalResults;
    }
}
