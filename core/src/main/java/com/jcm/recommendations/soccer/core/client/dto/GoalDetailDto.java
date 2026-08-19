package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoalDetailDto {

    @JsonProperty("player_id")
    private Long playerId;

    private String time;
    private String extra;

    @JsonProperty("assist_player_id")
    private Long assistPlayerId;
}
