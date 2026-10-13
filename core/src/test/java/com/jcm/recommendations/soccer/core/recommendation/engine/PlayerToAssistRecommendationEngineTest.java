package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jcm.recommendations.soccer.core.recommendation.engine.PlayerToScoreRecommendationEngineTest.context;
import static org.assertj.core.api.Assertions.assertThat;

class PlayerToAssistRecommendationEngineTest {

    private PlayerToAssistRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PlayerToAssistRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns PLAYER_TO_ASSIST")
    void getType_returnsPlayerToAssist() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.PLAYER_TO_ASSIST);
    }

    @Test
    @DisplayName("tracks the leading creator as an unpriced Moderate shadow pick")
    void analyze_picksBestCreator() {
        Recommendation rec = engine.analyze(context(15,
                List.of(creator(30L, "Playmaker", 8, 1500, 17)),
                List.of(creator(31L, "Wide Midfielder", 3, 1300, 16)))).orElseThrow();

        assertThat(rec.getMarket()).isEqualTo("Playmaker to assist");
        assertThat(rec.getOdds()).isNull();
        assertThat(rec.getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(rec.getScore()).isBetween(25.0, 35.0);
        assertThat(rec.getFactors())
                .containsEntry("shadow", true)
                .containsEntry("meetsPublishBar", true);
    }

    @Test
    @DisplayName("assist probabilities sit well below a comparable goalscorer's")
    void analyze_assistsAreRarerThanGoals() {
        PlayerSeasonStats player = creator(30L, "Playmaker", 8, 1500, 17);
        player.setGoalsOverall(8);
        player.setPenaltyGoals(0);

        double assist = engine.analyze(context(15, List.of(player), List.of())).orElseThrow().getScore();
        double goal = new PlayerToScoreRecommendationEngine()
                .analyze(context(15, List.of(player), List.of())).orElseThrow().getScore();

        assertThat(assist).isLessThan(goal);
    }

    @Test
    @DisplayName("a rare creator stays under the tracking floor")
    void analyze_marginalCreator_isEmpty() {
        assertThat(engine.analyze(context(15, List.of(creator(30L, "Deep Midfielder", 1, 1200, 16)), List.of())))
                .isEmpty();
    }

    @Test
    @DisplayName("falls back to the feed's assists per 90 when the season count is missing")
    void analyze_usesPer90FallBack() {
        PlayerSeasonStats player = creator(30L, "Playmaker", 8, 1500, 17);
        player.setAssistsOverall(null);
        player.setAssistsPer90Overall(0.48);

        assertThat(engine.analyze(context(15, List.of(player), List.of()))).isPresent();
    }

    private static PlayerSeasonStats creator(Long id, String knownAs, int assists, int minutes, int appearances) {
        return PlayerSeasonStats.builder()
                .playerId(id)
                .seasonId(100L)
                .knownAs(knownAs)
                .position("Midfielder")
                .appearancesOverall(appearances)
                .minutesPlayedOverall(minutes)
                .minPerMatch(85)
                .assistsOverall(assists)
                .build();
    }
}
