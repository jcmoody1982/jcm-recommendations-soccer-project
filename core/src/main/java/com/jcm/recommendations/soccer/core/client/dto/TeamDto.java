package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamDto {

    private Long id;
    private String name;
    private String cleanName;
    private String country;
    private String image;

    @JsonProperty("stadium_name")
    private String stadiumName;

    @JsonProperty("competition_id")
    private Long competitionId;

    @JsonProperty("table_position")
    private Integer tablePosition;

    private TeamStatsDto stats;

    public Integer getMatchesPlayed() {
        return stats != null ? stats.getMatchesPlayed() : null;
    }

    public Integer getPoints() {
        return stats != null ? stats.getPoints() : null;
    }

    public Integer getPosition() {
        return tablePosition != null ? tablePosition : (stats != null ? stats.getPosition() : null);
    }

    public Integer getSeasonWinsOverall() {
        return stats != null ? stats.getSeasonWinsOverall() : null;
    }

    public Integer getSeasonWinsHome() {
        return stats != null ? stats.getSeasonWinsHome() : null;
    }

    public Integer getSeasonWinsAway() {
        return stats != null ? stats.getSeasonWinsAway() : null;
    }

    public Integer getSeasonDrawsOverall() {
        return stats != null ? stats.getSeasonDrawsOverall() : null;
    }

    public Integer getSeasonDrawsHome() {
        return stats != null ? stats.getSeasonDrawsHome() : null;
    }

    public Integer getSeasonDrawsAway() {
        return stats != null ? stats.getSeasonDrawsAway() : null;
    }

    public Integer getSeasonLossesOverall() {
        return stats != null ? stats.getSeasonLossesOverall() : null;
    }

    public Integer getSeasonLossesHome() {
        return stats != null ? stats.getSeasonLossesHome() : null;
    }

    public Integer getSeasonLossesAway() {
        return stats != null ? stats.getSeasonLossesAway() : null;
    }

    public Integer getSeasonGoalsOverall() {
        return stats != null ? stats.getSeasonGoalsOverall() : null;
    }

    public Integer getSeasonGoalsHome() {
        return stats != null ? stats.getSeasonGoalsHome() : null;
    }

    public Integer getSeasonGoalsAway() {
        return stats != null ? stats.getSeasonGoalsAway() : null;
    }

    public Integer getSeasonConcededOverall() {
        return stats != null ? stats.getSeasonConcededOverall() : null;
    }

    public Integer getSeasonConcededHome() {
        return stats != null ? stats.getSeasonConcededHome() : null;
    }

    public Integer getSeasonConcededAway() {
        return stats != null ? stats.getSeasonConcededAway() : null;
    }

    public Integer getSeasonGoalDifference() {
        return stats != null ? stats.getSeasonGoalDifference() : null;
    }

    public Double getPpgOverall() {
        return stats != null ? stats.getPpgOverall() : null;
    }

    public Double getPpgHome() {
        return stats != null ? stats.getPpgHome() : null;
    }

    public Double getPpgAway() {
        return stats != null ? stats.getPpgAway() : null;
    }

    public Integer getSeasonBttsOverall() {
        return stats != null ? stats.getSeasonBttsOverall() : null;
    }

    public Integer getSeasonBttsHome() {
        return stats != null ? stats.getSeasonBttsHome() : null;
    }

    public Integer getSeasonBttsAway() {
        return stats != null ? stats.getSeasonBttsAway() : null;
    }

    public Double getSeasonBttsPercentageOverall() {
        return stats != null ? stats.getSeasonBttsPercentageOverall() : null;
    }

    public Double getSeasonBttsPercentageHome() {
        return stats != null ? stats.getSeasonBttsPercentageHome() : null;
    }

    public Double getSeasonBttsPercentageAway() {
        return stats != null ? stats.getSeasonBttsPercentageAway() : null;
    }

    public Integer getSeasonOver15Overall() {
        return stats != null ? stats.getSeasonOver15Overall() : null;
    }

    public Integer getSeasonOver25Overall() {
        return stats != null ? stats.getSeasonOver25Overall() : null;
    }

    public Integer getSeasonOver35Overall() {
        return stats != null ? stats.getSeasonOver35Overall() : null;
    }

    public Double getSeasonOver15PercentageOverall() {
        return stats != null ? stats.getSeasonOver15PercentageOverall() : null;
    }

    public Double getSeasonOver25PercentageOverall() {
        return stats != null ? stats.getSeasonOver25PercentageOverall() : null;
    }

    public Double getSeasonOver35PercentageOverall() {
        return stats != null ? stats.getSeasonOver35PercentageOverall() : null;
    }

    public Integer getSeasonCleanSheetsOverall() {
        return stats != null ? stats.getSeasonCleanSheetsOverall() : null;
    }

    public Integer getSeasonCleanSheetsHome() {
        return stats != null ? stats.getSeasonCleanSheetsHome() : null;
    }

    public Integer getSeasonCleanSheetsAway() {
        return stats != null ? stats.getSeasonCleanSheetsAway() : null;
    }

    public Integer getSeasonFailedToScoreOverall() {
        return stats != null ? stats.getSeasonFailedToScoreOverall() : null;
    }

    public Integer getSeasonFailedToScoreHome() {
        return stats != null ? stats.getSeasonFailedToScoreHome() : null;
    }

    public Integer getSeasonFailedToScoreAway() {
        return stats != null ? stats.getSeasonFailedToScoreAway() : null;
    }

    public Double getCornersAvgOverall() {
        return stats != null ? stats.getCornersAvgOverall() : null;
    }

    public Double getCornersAvgHome() {
        return stats != null ? stats.getCornersAvgHome() : null;
    }

    public Double getCornersAvgAway() {
        return stats != null ? stats.getCornersAvgAway() : null;
    }

    public Double getCardsAvgOverall() {
        return stats != null ? stats.getCardsAvgOverall() : null;
    }

    public Double getCardsAvgHome() {
        return stats != null ? stats.getCardsAvgHome() : null;
    }

    public Double getCardsAvgAway() {
        return stats != null ? stats.getCardsAvgAway() : null;
    }
}
