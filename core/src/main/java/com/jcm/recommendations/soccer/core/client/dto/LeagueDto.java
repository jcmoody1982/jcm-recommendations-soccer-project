package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueDto {

    private String name;
    private String image;
    private String country;
    private List<SeasonDto> season;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeasonDto {
        private Long id;
        private String year;
        private String country;
    }
}
