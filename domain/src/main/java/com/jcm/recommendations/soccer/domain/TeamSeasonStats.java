package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_season_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSeasonStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long teamId;
    private Long seasonId;

    private Integer matchesPlayed;
    private Integer points;
    private Integer position;

    private Integer seasonWinsOverall;
    private Integer seasonWinsHome;
    private Integer seasonWinsAway;

    private Integer seasonDrawsOverall;
    private Integer seasonDrawsHome;
    private Integer seasonDrawsAway;

    private Integer seasonLossesOverall;
    private Integer seasonLossesHome;
    private Integer seasonLossesAway;

    private Integer seasonGoalsOverall;
    private Integer seasonGoalsHome;
    private Integer seasonGoalsAway;

    private Integer seasonConcededOverall;
    private Integer seasonConcededHome;
    private Integer seasonConcededAway;

    private Integer seasonGoalDifference;

    private Double ppgOverall;
    private Double ppgHome;
    private Double ppgAway;

    private Integer seasonBttsOverall;
    private Integer seasonBttsHome;
    private Integer seasonBttsAway;
    private Double seasonBttsPercentageOverall;
    private Double seasonBttsPercentageHome;
    private Double seasonBttsPercentageAway;

    private Integer seasonOver15Overall;
    private Integer seasonOver25Overall;
    private Integer seasonOver35Overall;
    private Double seasonOver15PercentageOverall;
    private Double seasonOver25PercentageOverall;
    private Double seasonOver35PercentageOverall;

    private Integer seasonCleanSheetsOverall;
    private Integer seasonCleanSheetsHome;
    private Integer seasonCleanSheetsAway;

    private Integer seasonFailedToScoreOverall;
    private Integer seasonFailedToScoreHome;
    private Integer seasonFailedToScoreAway;

    private Double cornersAvgOverall;
    private Double cornersAvgHome;
    private Double cornersAvgAway;

    private Double cardsAvgOverall;
    private Double cardsAvgHome;
    private Double cardsAvgAway;
}
