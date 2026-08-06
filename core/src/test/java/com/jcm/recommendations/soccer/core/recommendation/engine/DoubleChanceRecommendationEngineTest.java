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

class DoubleChanceRecommendationEngineTest {

    private DoubleChanceRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DoubleChanceRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns DOUBLE_CHANCE")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.DOUBLE_CHANCE);
    }

    @Test
    @DisplayName("analyze recommends 1X for strong home team")
    void analyze_recommendsHomeDrawForStrongHome() {
        FixtureContext context = createStrongHomeContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.DOUBLE_CHANCE);
        assertThat(result.get().getMarket()).isEqualTo("Home/Draw (1X)");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze recommends X2 for strong away team")
    void analyze_recommendsDrawAwayForStrongAway() {
        FixtureContext context = createStrongAwayContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Draw/Away (X2)");
    }

    @Test
    @DisplayName("analyze detects home fortress")
    void analyze_detectsHomeFortress() {
        FixtureContext context = createFortressContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("homeFortress")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("fortress");
    }

    @Test
    @DisplayName("analyze detects poor traveler")
    void analyze_detectsPoorTraveler() {
        FixtureContext context = createPoorTravelerContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("awayPoorTraveler")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze detects road warrior")
    void analyze_detectsRoadWarrior() {
        FixtureContext context = createRoadWarriorContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("awayRoadWarrior")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze calculates value vs odds")
    void analyze_calculatesValueVsOdds() {
        FixtureContext context = createContextWithOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("implied1X");
        assertThat(result.get().getFactors()).containsKey("value1X");
    }

    @Test
    @DisplayName("analyze integrates xG data")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("homeXgFor");
        assertThat(result.get().getFactors()).containsKey("awayXgFor");
    }

    @Test
    @DisplayName("analyze tracks position gap")
    void analyze_tracksPositionGap() {
        FixtureContext context = createContextWithPositions();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homePosition");
        assertThat(result.get().getFactors()).containsKey("awayPosition");
        assertThat(result.get().getFactors()).containsKey("positionGap");
    }

    @Test
    @DisplayName("analyze tracks recent form")
    void analyze_tracksRecentForm() {
        FixtureContext context = createContextWithForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeLast5Wins");
        assertThat(result.get().getFactors()).containsKey("homeLast5Losses");
    }

    @Test
    @DisplayName("analyze tracks positive indicators")
    void analyze_tracksPositiveIndicators() {
        FixtureContext context = createFortressContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        @SuppressWarnings("unchecked")
        List<String> indicators = (List<String>) result.get().getFactors().get("positiveIndicators");
        assertThat(indicators).anyMatch(s -> s.contains("fortress"));
    }

    @Test
    @DisplayName("analyze tracks risk flags")
    void analyze_tracksRiskFlags() {
        FixtureContext context = createContextWithoutXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        @SuppressWarnings("unchecked")
        List<String> flags = (List<String>) result.get().getFactors().get("riskFlags");
        assertThat(flags).anyMatch(s -> s.contains("xG"));
    }

    @Test
    @DisplayName("analyze handles weak teams context")
    void analyze_withWeakTeams_handlesCorrectly() {
        FixtureContext context = createWeakTeamsContext();

        Optional<Recommendation> result = engine.analyze(context);

        // Double chance naturally has high combined probability (sum of 2 outcomes)
        // Even for weak teams, should still generate a recommendation
        assertThat(result).isPresent();
        // Confidence should not be STRONG for weak teams without value
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.WEAK);
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze handles no xG with redistributed weights")
    void analyze_withNoXgData_usesRedistributedWeights() {
        FixtureContext context = createContextWithoutXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze calculates double chance odds")
    void analyze_calculatesDoubleChanceOdds() {
        FixtureContext context = createContextWithOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getOdds()).isNotNull();
        assertThat(result.get().getOdds()).isGreaterThan(1.0);
        assertThat(result.get().getOdds()).isLessThan(2.0);
    }

    // Helper methods to create test contexts

    private FixtureContext createStrongHomeContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.2)
                .ppgOverall(1.8)
                .position(3)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonGoalsHome(32)
                .seasonConcededHome(14)
                .xgForAvgHome(2.0)
                .xgForAvgOverall(1.8)
                .xgAgainstAvgHome(0.8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.0)
                .ppgOverall(1.3)
                .position(14)
                .seasonWinsAway(4)
                .seasonDrawsAway(6)
                .seasonLossesAway(10)
                .seasonGoalsAway(16)
                .seasonConcededAway(28)
                .xgForAvgAway(1.0)
                .xgForAvgOverall(1.2)
                .xgAgainstAvgAway(1.4)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createStrongAwayContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.0)
                .ppgOverall(1.1)
                .position(16)
                .seasonWinsHome(4)
                .seasonDrawsHome(6)
                .seasonLossesHome(10)
                .seasonGoalsHome(16)
                .seasonConcededHome(30)
                .xgForAvgHome(0.9)
                .xgForAvgOverall(1.0)
                .xgAgainstAvgHome(1.5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.8)
                .ppgOverall(2.0)
                .position(2)
                .seasonWinsAway(10)
                .seasonDrawsAway(6)
                .seasonLossesAway(4)
                .seasonGoalsAway(30)
                .seasonConcededAway(14)
                .xgForAvgAway(1.8)
                .xgForAvgOverall(2.0)
                .xgAgainstAvgAway(0.8)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createFortressContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.4)
                .ppgOverall(2.0)
                .position(2)
                .seasonWinsHome(14)
                .seasonDrawsHome(5)
                .seasonLossesHome(1)  // Only 5% loss = 95% unbeaten > 60%
                .seasonGoalsHome(38)
                .seasonConcededHome(10)
                .xgForAvgHome(2.2)
                .xgForAvgOverall(2.0)
                .xgAgainstAvgHome(0.6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.2)
                .ppgOverall(1.4)
                .position(10)
                .seasonWinsAway(5)
                .seasonDrawsAway(7)
                .seasonLossesAway(8)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgAway(1.1)
                .xgForAvgOverall(1.3)
                .xgAgainstAvgAway(1.2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createPoorTravelerContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.8)
                .ppgOverall(1.6)
                .position(6)
                .seasonWinsHome(10)
                .seasonDrawsHome(5)
                .seasonLossesHome(5)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .xgForAvgHome(1.6)
                .xgForAvgOverall(1.5)
                .xgAgainstAvgHome(1.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(0.6)  // Poor away
                .ppgOverall(1.2)
                .position(15)
                .seasonWinsAway(2)  // 10% away win rate < 25%
                .seasonDrawsAway(6)
                .seasonLossesAway(12)
                .seasonGoalsAway(12)
                .seasonConcededAway(32)
                .xgForAvgAway(0.8)
                .xgForAvgOverall(1.1)
                .xgAgainstAvgAway(1.6)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createRoadWarriorContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.2)
                .ppgOverall(1.3)
                .position(12)
                .seasonWinsHome(5)
                .seasonDrawsHome(6)
                .seasonLossesHome(9)
                .seasonGoalsHome(20)
                .seasonConcededHome(26)
                .xgForAvgHome(1.1)
                .xgForAvgOverall(1.2)
                .xgAgainstAvgHome(1.3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.8)  // Strong away
                .ppgOverall(1.9)
                .position(3)
                .seasonWinsAway(10)  // 50% away win rate > 35%
                .seasonDrawsAway(5)
                .seasonLossesAway(5)  // 25% away loss = 75% unbeaten > 50%
                .seasonGoalsAway(28)
                .seasonConcededAway(16)
                .xgForAvgAway(1.7)
                .xgForAvgOverall(1.8)
                .xgAgainstAvgAway(0.9)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithOdds() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgOverall(1.8)
                .position(4)
                .seasonWinsHome(11)
                .seasonDrawsHome(5)
                .seasonLossesHome(4)
                .seasonGoalsHome(30)
                .seasonConcededHome(16)
                .xgForAvgHome(1.8)
                .xgForAvgOverall(1.7)
                .xgAgainstAvgHome(0.9)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.1)
                .ppgOverall(1.3)
                .position(12)
                .seasonWinsAway(4)
                .seasonDrawsAway(7)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(24)
                .xgForAvgAway(1.0)
                .xgForAvgOverall(1.2)
                .xgAgainstAvgAway(1.3)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(106L)
                .oddsFt1(1.80)  // Home
                .oddsFtX(3.50)  // Draw
                .oddsFt2(4.50)  // Away
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgOverall(1.8)
                .position(5)
                .seasonWinsHome(10)
                .seasonDrawsHome(6)
                .seasonLossesHome(4)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .xgForAvgHome(1.9)
                .xgForAvgOverall(1.7)
                .xgAgainstAvgHome(1.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.2)
                .ppgOverall(1.4)
                .position(11)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(24)
                .xgForAvgAway(1.1)
                .xgForAvgOverall(1.3)
                .xgAgainstAvgAway(1.3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithPositions() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgOverall(1.8)
                .position(3)  // High position
                .seasonWinsHome(10)
                .seasonDrawsHome(6)
                .seasonLossesHome(4)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .xgForAvgHome(1.8)
                .xgForAvgOverall(1.7)
                .xgAgainstAvgHome(1.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.0)
                .ppgOverall(1.2)
                .position(16)  // Low position - 13 gap
                .seasonWinsAway(4)
                .seasonDrawsAway(6)
                .seasonLossesAway(10)
                .seasonGoalsAway(16)
                .seasonConcededAway(28)
                .xgForAvgAway(0.9)
                .xgForAvgOverall(1.1)
                .xgAgainstAvgAway(1.4)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithForm() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgOverall(1.8)
                .position(5)
                .seasonWinsHome(10)
                .seasonDrawsHome(6)
                .seasonLossesHome(4)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .xgForAvgHome(1.8)
                .xgForAvgOverall(1.7)
                .xgAgainstAvgHome(1.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.1)
                .ppgOverall(1.3)
                .position(13)
                .seasonWinsAway(4)
                .seasonDrawsAway(7)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(24)
                .xgForAvgAway(1.0)
                .xgForAvgOverall(1.2)
                .xgAgainstAvgAway(1.3)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(4)
                .drawsHome(1)
                .lossesHome(0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(1)
                .drawsAway(2)
                .lossesAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createContextWithoutXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgOverall(1.8)
                .position(5)
                .seasonWinsHome(10)
                .seasonDrawsHome(6)
                .seasonLossesHome(4)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                // No xG data
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(1.1)
                .ppgOverall(1.3)
                .position(13)
                .seasonWinsAway(4)
                .seasonDrawsAway(7)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(24)
                // No xG data
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(110L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createWeakTeamsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.0)
                .ppgOverall(1.0)
                .position(17)
                .seasonWinsHome(3)
                .seasonDrawsHome(5)
                .seasonLossesHome(12)
                .seasonGoalsHome(14)
                .seasonConcededHome(32)
                .xgForAvgHome(0.8)
                .xgForAvgOverall(0.9)
                .xgAgainstAvgHome(1.6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgAway(0.8)
                .ppgOverall(1.0)
                .position(18)
                .seasonWinsAway(2)
                .seasonDrawsAway(5)
                .seasonLossesAway(13)
                .seasonGoalsAway(12)
                .seasonConcededAway(35)
                .xgForAvgAway(0.7)
                .xgForAvgOverall(0.9)
                .xgAgainstAvgAway(1.8)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(111L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(112L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();
    }

    private Fixture createFixture(Long id) {
        return Fixture.builder()
                .id(id)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .seasonId(1L)
                .status("upcoming")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
