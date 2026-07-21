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
@Table(name = "team_recent_form")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRecentForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long teamId;
    private Long competitionId;

    private Integer winsOverall;
    private Integer winsHome;
    private Integer winsAway;

    private Integer drawsOverall;
    private Integer drawsHome;
    private Integer drawsAway;

    private Integer lossesOverall;
    private Integer lossesHome;
    private Integer lossesAway;

    private Double ppgOverall;
    private Double ppgHome;
    private Double ppgAway;

    private Integer goalsOverall;
    private Integer goalsHome;
    private Integer goalsAway;

    private Integer concededOverall;
    private Integer concededHome;
    private Integer concededAway;

    private Double scoredAvgOverall;
    private Double scoredAvgHome;
    private Double scoredAvgAway;

    private Double concededAvgOverall;
    private Double concededAvgHome;
    private Double concededAvgAway;

    private Integer bttsOverall;
    private Integer bttsHome;
    private Integer bttsAway;
    private Double bttsPercentageOverall;
    private Double bttsPercentageHome;
    private Double bttsPercentageAway;

    private Integer over15Overall;
    private Integer over25Overall;
    private Integer over35Overall;
    private Double over15PercentageOverall;
    private Double over25PercentageOverall;
    private Double over35PercentageOverall;

    private Integer cleanSheetsOverall;
    private Integer cleanSheetsHome;
    private Integer cleanSheetsAway;

    private Integer failedToScoreOverall;
    private Integer failedToScoreHome;
    private Integer failedToScoreAway;

    private Double cornersAvgOverall;
    private Double cornersAvgHome;
    private Double cornersAvgAway;

    private Double cardsAvgOverall;
    private Double cardsAvgHome;
    private Double cardsAvgAway;

    private Double foulsAvgOverall;
    private Double foulsAvgHome;
    private Double foulsAvgAway;
}
