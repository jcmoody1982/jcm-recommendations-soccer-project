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
@Table(name = "referee_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long refereeId;
    private Long seasonId;

    private Integer appearancesOverall;

    private Integer winsHome;
    private Integer winsAway;
    private Integer drawsOverall;

    private Double winsPerHome;
    private Double winsPerAway;
    private Double drawsPer;

    private Integer goalsOverall;
    private Integer goalsHome;
    private Integer goalsAway;
    private Double goalsPerMatchOverall;
    private Double goalsPerMatchHome;
    private Double goalsPerMatchAway;

    private Integer bttsOverall;
    private Double bttsPercentage;

    private Integer penaltiesGivenOverall;
    private Integer penaltiesGivenHome;
    private Integer penaltiesGivenAway;
    private Double penaltiesGivenPerMatchOverall;
    private Double penaltiesGivenPercentageOverall;

    private Integer cardsOverall;
    private Integer cardsHome;
    private Integer cardsAway;
    private Double cardsPerMatchOverall;
    private Double cardsPerMatchHome;
    private Double cardsPerMatchAway;

    private Integer yellowCardsOverall;
    private Integer redCardsOverall;

    private Integer over05CardsOverall;
    private Integer over15CardsOverall;
    private Integer over25CardsOverall;
    private Integer over35CardsOverall;
    private Integer over45CardsOverall;
    private Integer over55CardsOverall;
    private Integer over65CardsOverall;

    private Double over05CardsPercentageOverall;
    private Double over15CardsPercentageOverall;
    private Double over25CardsPercentageOverall;
    private Double over35CardsPercentageOverall;
    private Double over45CardsPercentageOverall;

    private Double minPerCardOverall;
    private Double minPerGoalOverall;
}
