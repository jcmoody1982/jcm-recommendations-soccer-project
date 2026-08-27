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

    public Double getScoredAvgOverall() {
        return stats != null ? stats.getScoredAvgOverall() : null;
    }

    public Double getScoredAvgHome() {
        return stats != null ? stats.getScoredAvgHome() : null;
    }

    public Double getScoredAvgAway() {
        return stats != null ? stats.getScoredAvgAway() : null;
    }

    public Double getConcededAvgOverall() {
        return stats != null ? stats.getConcededAvgOverall() : null;
    }

    public Double getConcededAvgHome() {
        return stats != null ? stats.getConcededAvgHome() : null;
    }

    public Double getConcededAvgAway() {
        return stats != null ? stats.getConcededAvgAway() : null;
    }

    public Double getFoulsAvgOverall() {
        return stats != null ? stats.getFoulsAvgOverall() : null;
    }

    public Double getFoulsAvgHome() {
        return stats != null ? stats.getFoulsAvgHome() : null;
    }

    public Double getFoulsAvgAway() {
        return stats != null ? stats.getFoulsAvgAway() : null;
    }

    public Double getScoredAvgHtOverall() {
        return stats != null ? stats.getScoredAvgHtOverall() : null;
    }

    public Double getScoredAvgHtHome() {
        return stats != null ? stats.getScoredAvgHtHome() : null;
    }

    public Double getScoredAvgHtAway() {
        return stats != null ? stats.getScoredAvgHtAway() : null;
    }

    public Double getConcededAvgHtOverall() {
        return stats != null ? stats.getConcededAvgHtOverall() : null;
    }

    public Double getConcededAvgHtHome() {
        return stats != null ? stats.getConcededAvgHtHome() : null;
    }

    public Double getConcededAvgHtAway() {
        return stats != null ? stats.getConcededAvgHtAway() : null;
    }

    public Double getScoredAvg2hOverall() {
        return stats != null ? stats.getScoredAvg2hOverall() : null;
    }

    public Double getScoredAvg2hHome() {
        return stats != null ? stats.getScoredAvg2hHome() : null;
    }

    public Double getScoredAvg2hAway() {
        return stats != null ? stats.getScoredAvg2hAway() : null;
    }

    public Double getConcededAvg2hOverall() {
        return stats != null ? stats.getConcededAvg2hOverall() : null;
    }

    public Double getConcededAvg2hHome() {
        return stats != null ? stats.getConcededAvg2hHome() : null;
    }

    public Double getConcededAvg2hAway() {
        return stats != null ? stats.getConcededAvg2hAway() : null;
    }

    public Double getBttsFhgPercentageOverall() {
        return stats != null ? stats.getBttsFhgPercentageOverall() : null;
    }

    public Double getBttsFhgPercentageHome() {
        return stats != null ? stats.getBttsFhgPercentageHome() : null;
    }

    public Double getBttsFhgPercentageAway() {
        return stats != null ? stats.getBttsFhgPercentageAway() : null;
    }

    public Double getBtts2hgPercentageOverall() {
        return stats != null ? stats.getBtts2hgPercentageOverall() : null;
    }

    public Double getBtts2hgPercentageHome() {
        return stats != null ? stats.getBtts2hgPercentageHome() : null;
    }

    public Double getBtts2hgPercentageAway() {
        return stats != null ? stats.getBtts2hgPercentageAway() : null;
    }

    public Double getShotsAvgOverall() {
        return stats != null ? stats.getShotsAvgOverall() : null;
    }

    public Double getShotsAvgHome() {
        return stats != null ? stats.getShotsAvgHome() : null;
    }

    public Double getShotsAvgAway() {
        return stats != null ? stats.getShotsAvgAway() : null;
    }

    // xG (Expected Goals) getters
    public Double getXgForAvgOverall() {
        return stats != null ? stats.getXgForAvgOverall() : null;
    }

    public Double getXgForAvgHome() {
        return stats != null ? stats.getXgForAvgHome() : null;
    }

    public Double getXgForAvgAway() {
        return stats != null ? stats.getXgForAvgAway() : null;
    }

    public Double getXgAgainstAvgOverall() {
        return stats != null ? stats.getXgAgainstAvgOverall() : null;
    }

    public Double getXgAgainstAvgHome() {
        return stats != null ? stats.getXgAgainstAvgHome() : null;
    }

    public Double getXgAgainstAvgAway() {
        return stats != null ? stats.getXgAgainstAvgAway() : null;
    }
}
