package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TopVsBottomRecommendationEngineTest {

    private TopVsBottomRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TopVsBottomRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns TOP_VS_BOTTOM")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.TOP_VS_BOTTOM);
    }

    @Test
    @DisplayName("analyze skips when home team is not table favorite")
    void analyze_skipsAwayFavorite() {
        FixtureContext context = mismatchBuilder(1L)
                .homeTeamStats(TeamSeasonStats.builder().teamId(1L).position(14).ppgHome(0.8).build())
                .awayTeamStats(TeamSeasonStats.builder().teamId(2L).position(3).ppgAway(2.0).build())
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze skips when position gap is below moderate floor")
    void analyze_skipsSmallGap() {
        FixtureContext context = mismatchBuilder(2L)
                .homeTeamStats(TeamSeasonStats.builder().teamId(1L).position(8).ppgHome(2.0)
                        .seasonGoalDifference(10).matchesPlayed(10).build())
                .awayTeamStats(TeamSeasonStats.builder().teamId(2L).position(15).ppgAway(0.8)
                        .seasonGoalDifference(-8).matchesPlayed(10).build())
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze recommends home favorite win for clear mismatch")
    void analyze_recommendsHomeFavoriteWin() {
        FixtureContext context = strongHomeMismatch(3L);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Top Team");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
        assertThat(result.get().getFactors().get("homeIsFavorite")).isEqualTo(true);
        assertThat(result.get().getFactors().get("primaryMarketType")).isEqualTo("FAVORITE_WIN");
    }

    @Test
    @DisplayName("analyze pivots to Over 2.5 when favorite odds are short")
    void analyze_shortOddsPivotsToGoalsLine() {
        FixtureContext context = mismatchBuilder(4L)
                .homeTeamStats(homeStatsForMismatch())
                .awayTeamStats(awayStatsForMismatch())
                .homeTeamForm(homeFormForMismatch())
                .awayTeamForm(awayFormForMismatch())
                .odds(FixtureOdds.builder()
                        .fixtureId(4L)
                        .oddsFt1(1.25)
                        .oddsFtX(5.50)
                        .oddsFt2(11.00)
                        .oddsFtOver35(2.10)
                        .build())
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 3.5 Goals");
        assertThat(result.get().getFactors().get("flag")).isEqualTo("Goals Expected");
        assertThat(result.get().getFactors().get("primaryMarketType")).isEqualTo("GOALS_LINE");
    }

    @Test
    @DisplayName("analyze flags upset watch and pivots to 1X when away side is live")
    void analyze_upsetWatchPivotsToDoubleChance() {
        FixtureContext context = mismatchBuilder(5L)
                .homeTeamStats(homeStatsForMismatch())
                .awayTeamStats(TeamSeasonStats.builder()
                        .teamId(2L)
                        .position(16)
                        .matchesPlayed(20)
                        .seasonWinsAway(6)
                        .seasonDrawsAway(4)
                        .seasonLossesAway(10)
                        .seasonGoalsAway(18)
                        .seasonConcededAway(28)
                        .seasonFailedToScoreAway(12)
                        .seasonGoalDifference(-10)
                        .ppgAway(1.0)
                        .build())
                .homeTeamForm(TeamRecentForm.builder()
                        .teamId(1L)
                        .winsHome(2)
                        .drawsHome(1)
                        .lossesHome(2)
                        .ppgHome(1.4)
                        .build())
                .awayTeamForm(TeamRecentForm.builder()
                        .teamId(2L)
                        .winsAway(1)
                        .drawsAway(2)
                        .lossesAway(2)
                        .ppgAway(1.6)
                        .build())
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Home/Draw (1X)");
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(result.get().getFactors().get("flag")).isEqualTo("Upset Watch");
        assertThat((Integer) result.get().getFactors().get("upsetFactorCount")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("analyze skips when too many upset factors are present")
    void analyze_skipsWhenUpsetRiskTooHigh() {
        FixtureContext context = mismatchBuilder(6L)
                .homeTeamStats(homeStatsForMismatch())
                .awayTeamStats(TeamSeasonStats.builder()
                        .teamId(2L)
                        .position(16)
                        .matchesPlayed(20)
                        .seasonWinsAway(9)
                        .seasonDrawsAway(4)
                        .seasonLossesAway(7)
                        .seasonGoalsAway(28)
                        .seasonConcededAway(26)
                        .seasonFailedToScoreAway(4)
                        .seasonGoalDifference(0)
                        .ppgAway(1.1)
                        .build())
                .homeTeamForm(TeamRecentForm.builder()
                        .teamId(1L)
                        .winsHome(1)
                        .drawsHome(1)
                        .lossesHome(3)
                        .ppgHome(1.0)
                        .build())
                .awayTeamForm(TeamRecentForm.builder()
                        .teamId(2L)
                        .winsAway(3)
                        .drawsAway(1)
                        .lossesAway(1)
                        .ppgAway(2.2)
                        .scoredAvgAway(1.8)
                        .build())
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze exposes BTTS mismatch as alternative market")
    void analyze_suggestsBttsAlternative() {
        FixtureContext context = mismatchBuilder(7L)
                .homeTeamStats(TeamSeasonStats.builder()
                        .teamId(1L)
                        .position(2)
                        .matchesPlayed(20)
                        .seasonWinsHome(12)
                        .seasonDrawsHome(4)
                        .seasonLossesHome(4)
                        .seasonGoalsHome(34)
                        .seasonConcededHome(18)
                        .seasonCleanSheetsHome(6)
                        .seasonGoalDifference(20)
                        .ppgHome(2.2)
                        .build())
                .awayTeamStats(awayStatsForMismatch())
                .homeTeamForm(homeFormForMismatch())
                .awayTeamForm(awayFormForMismatch())
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("bttsMismatchSuggested")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> alternatives = (List<String>) result.get().getFactors().get("alternativeMarkets");
        assertThat(alternatives).contains("BTTS Yes");
    }

    private FixtureContext strongHomeMismatch(long fixtureId) {
        return mismatchBuilder(fixtureId)
                .homeTeamStats(homeStatsForMismatch())
                .awayTeamStats(awayStatsForMismatch())
                .homeTeamForm(homeFormForMismatch())
                .awayTeamForm(awayFormForMismatch())
                .build();
    }

    private TeamSeasonStats homeStatsForMismatch() {
        return TeamSeasonStats.builder()
                .teamId(1L)
                .position(2)
                .matchesPlayed(20)
                .seasonWinsHome(13)
                .seasonDrawsHome(4)
                .seasonLossesHome(3)
                .seasonGoalsHome(36)
                .seasonConcededHome(14)
                .seasonCleanSheetsHome(8)
                .seasonGoalDifference(22)
                .ppgHome(2.15)
                .build();
    }

    private TeamSeasonStats awayStatsForMismatch() {
        return TeamSeasonStats.builder()
                .teamId(2L)
                .position(16)
                .matchesPlayed(20)
                .seasonWinsAway(2)
                .seasonDrawsAway(5)
                .seasonLossesAway(13)
                .seasonGoalsAway(14)
                .seasonConcededAway(34)
                .seasonFailedToScoreAway(10)
                .seasonGoalDifference(-16)
                .ppgAway(0.65)
                .build();
    }

    private TeamRecentForm homeFormForMismatch() {
        return TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(4)
                .drawsHome(1)
                .lossesHome(0)
                .ppgHome(2.5)
                .build();
    }

    private TeamRecentForm awayFormForMismatch() {
        return TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(0)
                .drawsAway(1)
                .lossesAway(4)
                .ppgAway(0.4)
                .build();
    }

    private FixtureContext.FixtureContextBuilder mismatchBuilder(long fixtureId) {
        return FixtureContext.builder()
                .fixture(Fixture.builder().id(fixtureId).dateUnix(1_700_000_000L).build())
                .homeTeam(Team.builder().id(1L).name("Top Team").build())
                .awayTeam(Team.builder().id(2L).name("Bottom Team").build())
                .league(League.builder().name("Test League").build());
    }
}
