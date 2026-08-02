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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HomeAwaySpecialistEngineTest {

    private HomeAwaySpecialistEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HomeAwaySpecialistEngine();
    }

    @Test
    @DisplayName("getType returns HOME_AWAY_SPECIALIST")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.HOME_AWAY_SPECIALIST);
    }

    @Test
    @DisplayName("analyze detects strong home specialist")
    void analyze_detectsStrongHomeSpecialist() {
        FixtureContext context = createStrongHomeSpecialistContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.HOME_AWAY_SPECIALIST);
        assertThat(result.get().getFactors().get("classification").toString()).contains("Home Specialist");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze detects home fortress")
    void analyze_detectsHomeFortress() {
        FixtureContext context = createHomeFortressContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("classification")).isEqualTo("Home Fortress");
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze detects poor traveler")
    void analyze_detectsPoorTraveler() {
        FixtureContext context = createPoorTravelerContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("classification").toString()).contains("Poor Traveler");
        // Poor traveler = back home team
        assertThat(result.get().getMarket()).isEqualTo("Home Team");
    }

    @Test
    @DisplayName("analyze detects away specialist")
    void analyze_detectsAwaySpecialist() {
        FixtureContext context = createAwaySpecialistContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("classification").toString()).contains("Away");
        assertThat(result.get().getMarket()).isEqualTo("Away Team");
    }

    @Test
    @DisplayName("analyze returns empty for balanced team")
    void analyze_withBalancedTeam_returnsEmpty() {
        FixtureContext context = createBalancedTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze integrates xG data when available")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("xgDisparity");
        assertThat(result.get().getFactors()).containsKey("homeXgAvg");
        assertThat(result.get().getFactors()).containsKey("awayXgAvg");
    }

    @Test
    @DisplayName("analyze tracks all disparity metrics")
    void analyze_tracksAllDisparities() {
        FixtureContext context = createStrongHomeSpecialistContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("ppgDisparity");
        assertThat(result.get().getFactors()).containsKey("winDisparity");
        assertThat(result.get().getFactors()).containsKey("goalsDisparity");
        assertThat(result.get().getFactors()).containsKey("concededDisparity");
        assertThat(result.get().getFactors()).containsKey("overallDisparityScore");
    }

    @Test
    @DisplayName("analyze tracks form context")
    void analyze_tracksFormContext() {
        FixtureContext context = createContextWithFormData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("formPpgDivergence");
        assertThat(result.get().getFactors()).containsKey("formGoalsDivergence");
        assertThat(result.get().getFactors()).containsKey("formDivergesFromSeason");
    }

    @Test
    @DisplayName("analyze detects form divergence")
    void analyze_detectsFormDivergence() {
        FixtureContext context = createContextWithDivergentForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDivergesFromSeason")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("formStatus");
    }

    @Test
    @DisplayName("analyze tracks all candidates found")
    void analyze_tracksAllCandidates() {
        FixtureContext context = createContextWithMultipleCandidates();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("candidatesFound");
        assertThat(result.get().getFactors()).containsKey("allCandidates");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allCandidates = (List<Map<String, Object>>) result.get().getFactors().get("allCandidates");
        assertThat(allCandidates).isNotEmpty();
    }

    @Test
    @DisplayName("analyze tracks positive indicators and risk flags")
    void analyze_tracksIndicatorsAndFlags() {
        FixtureContext context = createStrongHomeSpecialistContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        assertThat(result.get().getFactors().get("positiveIndicators")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze selects best candidate by disparity score")
    void analyze_selectsBestCandidate() {
        FixtureContext context = createHomeFortressContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        // Fortress should score highest
        assertThat(result.get().getFactors().get("classification")).isEqualTo("Home Fortress");
    }

    // Helper methods to create test contexts

    private FixtureContext createStrongHomeSpecialistContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.2)       // Strong at home
                .ppgAway(1.2)       // Weak away - 1.0 PPG diff
                .seasonWinsHome(11)
                .seasonDrawsHome(5)
                .seasonLossesHome(4) // 20% loss rate - doesn't qualify for Fortress
                .seasonWinsAway(5)
                .seasonDrawsAway(5)
                .seasonLossesAway(10)
                .seasonGoalsHome(30)
                .seasonGoalsAway(16)
                .seasonConcededHome(18) // 0.9 per game - doesn't qualify for Fortress < 0.8
                .seasonConcededAway(26)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.6)
                .ppgAway(1.4)
                .seasonWinsHome(8)
                .seasonDrawsHome(6)
                .seasonLossesHome(6)
                .seasonWinsAway(6)
                .seasonDrawsAway(5)
                .seasonLossesAway(9)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createHomeFortressContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.6)
                .ppgAway(1.5)
                .seasonWinsHome(16)  // 80% win rate
                .seasonDrawsHome(3)
                .seasonLossesHome(1) // Only 5% loss rate
                .seasonWinsAway(8)
                .seasonDrawsAway(6)
                .seasonLossesAway(6)
                .seasonGoalsHome(40)
                .seasonGoalsAway(22)
                .seasonConcededHome(10) // < 0.8 per game
                .seasonConcededAway(22)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsHome(20)
                .seasonGoalsAway(16)
                .seasonConcededHome(22)
                .seasonConcededAway(28)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
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
                .ppgAway(1.4)
                .seasonWinsHome(10)
                .seasonDrawsHome(5)
                .seasonLossesHome(5)
                .seasonWinsAway(6)
                .seasonDrawsAway(5)
                .seasonLossesAway(9)
                .seasonGoalsHome(28)
                .seasonGoalsAway(20)
                .seasonConcededHome(20)
                .seasonConcededAway(26)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.6)
                .ppgAway(0.6)       // Very poor away PPG < 0.8
                .seasonWinsHome(8)
                .seasonDrawsHome(6)
                .seasonLossesHome(6)
                .seasonWinsAway(2)  // 10% away win rate < 20%
                .seasonDrawsAway(6)
                .seasonLossesAway(12)
                .seasonGoalsHome(22)
                .seasonGoalsAway(10) // < 0.8 goals per game away
                .seasonConcededHome(20)
                .seasonConcededAway(32)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createAwaySpecialistContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.6)
                .ppgAway(2.0)       // Better away than home (rare)
                .seasonWinsHome(8)
                .seasonDrawsHome(6)
                .seasonLossesHome(6)
                .seasonWinsAway(12) // 60% away win rate
                .seasonDrawsAway(4)
                .seasonLossesAway(4)
                .seasonGoalsHome(24)
                .seasonGoalsAway(32)
                .seasonConcededHome(22)
                .seasonConcededAway(16)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createBalancedTeamContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.4)       // Very close to home
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(6)
                .seasonDrawsAway(7)
                .seasonLossesAway(7)
                .seasonGoalsHome(22)
                .seasonGoalsAway(20)
                .seasonConcededHome(22)
                .seasonConcededAway(24)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(7)
                .seasonLossesAway(8)
                .seasonGoalsHome(21)
                .seasonGoalsAway(18)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.2)
                .ppgAway(1.2)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonWinsAway(5)
                .seasonDrawsAway(5)
                .seasonLossesAway(10)
                .seasonGoalsHome(32)
                .seasonGoalsAway(16)
                .seasonConcededHome(14)
                .seasonConcededAway(28)
                .xgForAvgHome(2.0)    // Strong xG at home
                .xgForAvgAway(1.0)    // Weak xG away
                .xgAgainstAvgHome(0.8)
                .xgAgainstAvgAway(1.4)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsHome(20)
                .seasonGoalsAway(17)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .xgForAvgHome(1.3)
                .xgForAvgAway(1.1)
                .xgAgainstAvgHome(1.2)
                .xgAgainstAvgAway(1.4)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithFormData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.2)
                .ppgAway(1.1)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonWinsAway(4)
                .seasonDrawsAway(6)
                .seasonLossesAway(10)
                .seasonGoalsHome(32)
                .seasonGoalsAway(16)
                .seasonConcededHome(14)
                .seasonConcededAway(28)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsHome(20)
                .seasonGoalsAway(17)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.4)       // Slightly better than season
                .ppgAway(1.2)
                .scoredAvgHome(1.8)
                .scoredAvgAway(0.9)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .ppgHome(1.4)
                .ppgAway(1.2)
                .scoredAvgHome(1.1)
                .scoredAvgAway(0.9)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createContextWithDivergentForm() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.0)
                .ppgAway(1.0)
                .seasonWinsHome(11)
                .seasonDrawsHome(5)
                .seasonLossesHome(4)
                .seasonWinsAway(4)
                .seasonDrawsAway(5)
                .seasonLossesAway(11)
                .seasonGoalsHome(30)
                .seasonGoalsAway(14)
                .seasonConcededHome(16)
                .seasonConcededAway(30)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.3)
                .seasonWinsHome(7)
                .seasonDrawsHome(7)
                .seasonLossesHome(6)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsHome(20)
                .seasonGoalsAway(17)
                .seasonConcededHome(22)
                .seasonConcededAway(26)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.6)       // Much better than season (divergent)
                .ppgAway(1.0)
                .scoredAvgHome(2.2)
                .scoredAvgAway(0.8)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .ppgHome(1.4)
                .ppgAway(1.2)
                .scoredAvgHome(1.0)
                .scoredAvgAway(0.9)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createContextWithMultipleCandidates() {
        // Both home specialist AND poor traveler should be detected
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.2)
                .ppgAway(1.2)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonWinsAway(5)
                .seasonDrawsAway(5)
                .seasonLossesAway(10)
                .seasonGoalsHome(32)
                .seasonGoalsAway(16)
                .seasonConcededHome(14)
                .seasonConcededAway(28)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.8)
                .ppgAway(0.5)       // Poor traveler
                .seasonWinsHome(10)
                .seasonDrawsHome(4)
                .seasonLossesHome(6)
                .seasonWinsAway(2)  // Very low away win rate
                .seasonDrawsAway(4)
                .seasonLossesAway(14)
                .seasonGoalsHome(26)
                .seasonGoalsAway(8)
                .seasonConcededHome(20)
                .seasonConcededAway(35)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(110L))
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
