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
        @com.fasterxml.jackson.annotation.JsonProperty("current_page")
        private int currentPage;

        @com.fasterxml.jackson.annotation.JsonProperty("max_page")
        private int maxPage;

        @com.fasterxml.jackson.annotation.JsonProperty("results_per_page")
        private int resultsPerPage;

        @com.fasterxml.jackson.annotation.JsonProperty("total_results")
        private int totalResults;
    }
}
