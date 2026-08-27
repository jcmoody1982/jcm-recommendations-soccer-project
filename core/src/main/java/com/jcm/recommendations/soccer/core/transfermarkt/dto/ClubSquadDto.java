package com.jcm.recommendations.soccer.core.transfermarkt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClubSquadDto {

    @JsonProperty("club_id")
    private Long clubId;

    @JsonProperty("club_name")
    private String clubName;

    @JsonProperty("total_market_value_eur")
    private Long totalMarketValueEur;

    @JsonProperty("avg_market_value_eur")
    private Long avgMarketValueEur;

    @JsonProperty("squad_size")
    private Integer squadSize;

    @JsonProperty("avg_age")
    private Double avgAge;

    @JsonProperty("foreign_players")
    private Integer foreignPlayers;

    private String season;
}
