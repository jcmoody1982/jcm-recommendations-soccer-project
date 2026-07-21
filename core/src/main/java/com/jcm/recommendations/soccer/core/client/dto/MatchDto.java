package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchDto {

    private Long id;

    @JsonProperty("homeID")
    private Long homeId;

    @JsonProperty("awayID")
    private Long awayId;

    @JsonProperty("home_name")
    private String homeName;

    @JsonProperty("away_name")
    private String awayName;

    @JsonProperty("date_unix")
    private Long dateUnix;

    private String status;

    @JsonProperty("game_week")
    private Integer gameWeek;

    @JsonProperty("stadium_name")
    private String stadiumName;

    @JsonProperty("stadium_location")
    private String stadiumLocation;

    @JsonProperty("competition_id")
    private Long competitionId;

    @JsonProperty("refereeID")
    private Long refereeId;

    private String referee;

    @JsonProperty("homeGoalCount")
    private Integer homeGoalCount;

    @JsonProperty("awayGoalCount")
    private Integer awayGoalCount;

    @JsonProperty("totalGoalCount")
    private Integer totalGoalCount;

    @JsonProperty("odds_ft_1")
    private Double oddsFt1;

    @JsonProperty("odds_ft_x")
    private Double oddsFtX;

    @JsonProperty("odds_ft_2")
    private Double oddsFt2;

    @JsonProperty("odds_ft_over05")
    private Double oddsFtOver05;

    @JsonProperty("odds_ft_over15")
    private Double oddsFtOver15;

    @JsonProperty("odds_ft_over25")
    private Double oddsFtOver25;

    @JsonProperty("odds_ft_over35")
    private Double oddsFtOver35;

    @JsonProperty("odds_ft_over45")
    private Double oddsFtOver45;

    @JsonProperty("odds_ft_under05")
    private Double oddsFtUnder05;

    @JsonProperty("odds_ft_under15")
    private Double oddsFtUnder15;

    @JsonProperty("odds_ft_under25")
    private Double oddsFtUnder25;

    @JsonProperty("odds_ft_under35")
    private Double oddsFtUnder35;

    @JsonProperty("odds_ft_under45")
    private Double oddsFtUnder45;

    @JsonProperty("odds_btts_yes")
    private Double oddsBttsYes;

    @JsonProperty("odds_btts_no")
    private Double oddsBttsNo;

    @JsonProperty("btts_potential")
    private Double bttsPotential;

    @JsonProperty("o15_potential")
    private Double o15Potential;

    @JsonProperty("o25_potential")
    private Double o25Potential;

    @JsonProperty("o35_potential")
    private Double o35Potential;

    @JsonProperty("o45_potential")
    private Double o45Potential;

    @JsonProperty("o05HT_potential")
    private Double o05HtPotential;

    @JsonProperty("o15HT_potential")
    private Double o15HtPotential;

    @JsonProperty("u15_potential")
    private Double u15Potential;

    @JsonProperty("avg_potential")
    private Double avgPotential;

    @JsonProperty("corners_potential")
    private Double cornersPotential;

    @JsonProperty("corners_o85_potential")
    private Double cornersO85Potential;

    @JsonProperty("corners_o95_potential")
    private Double cornersO95Potential;

    @JsonProperty("corners_o105_potential")
    private Double cornersO105Potential;

    @JsonProperty("cards_potential")
    private Double cardsPotential;

    @JsonProperty("offsides_potential")
    private Double offsidesPotential;
}
