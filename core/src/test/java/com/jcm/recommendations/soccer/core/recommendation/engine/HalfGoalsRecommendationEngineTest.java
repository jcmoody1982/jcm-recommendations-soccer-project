package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HalfGoalsRecommendationEngineTest {

    private HalfGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HalfGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("second half Over 0.5 score never exceeds 100% after intensity boost")
    void analyze_secondHalfScore_cappedAt100() {
        FixtureContext context = createHighSecondHalfScoringCloseTableContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.SECOND_HALF_GOALS);
        assertThat(result.get().getMarket()).isEqualTo("Over 0.5 Second Half Goals");
        assertThat(result.get().getScore()).isLessThanOrEqualTo(100.0);
    }

    private FixtureContext createHighSecondHalfScoringCloseTableContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(40)
                .scoredAvg2hHome(1.2)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(36)
                .scoredAvg2hAway(1.1)
                .position(7)
                .build();

        return FixtureContext.builder()
                .fixture(Fixture.builder().id(1L).homeTeamId(1L).awayTeamId(2L).seasonId(1L).build())
                .homeTeam(Team.builder().id(1L).name("Home").build())
                .awayTeam(Team.builder().id(2L).name("Away").build())
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }
}
