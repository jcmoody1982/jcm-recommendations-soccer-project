package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CornersRecommendationEngineTest {

    private CornersRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CornersRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns OVER_CORNERS")
    void getType_returnsOverCorners() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_CORNERS);
    }

    @Test
    @DisplayName("analyze returns Over 10.5 for high corner teams")
    void analyze_withHighCornerTeams_returnsOver105() {
        FixtureContext context = createHighCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_CORNERS);
        assertThat(result.get().getMarket()).contains("Over");
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze returns Under 9.5 for low corner teams")
    void analyze_withLowCornerTeams_returnsUnder() {
        FixtureContext context = createLowCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.UNDER_CORNERS);
        assertThat(result.get().getMarket()).contains("Under");
    }

    @Test
    @DisplayName("analyze returns empty for average corner teams")
    void analyze_withAverageCorners_returnsEmpty() {
        FixtureContext context = createAverageCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze tracks API potentials")
    void analyze_tracksApiPotentials() {
        FixtureContext context = createHighCornersContextWithPotentials();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("apiCornersO95Potential");
        assertThat(result.get().getFactors()).containsKey("apiCornersO105Potential");
    }

    @Test
    @DisplayName("analyze applies API confidence boost")
    void analyze_appliesApiConfidenceBoost() {
        FixtureContext context = createModerateCornersWithHighApiPotential();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("apiConfidenceBoostApplied")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("API potential boost");
    }

    @Test
    @DisplayName("analyze tracks form data availability")
    void analyze_tracksFormDataAvailability() {
        FixtureContext context = createHighCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("formDataAvailable");
    }

    @Test
    @DisplayName("analyze applies trend multiplier when trending up")
    void analyze_appliesTrendMultiplierUp() {
        FixtureContext context = createCornersWithUpwardTrend();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("trendDirection")).isEqualTo("UP");
        assertThat(result.get().getDescription()).contains("trending up");
    }

    @Test
    @DisplayName("analyze applies trend multiplier when trending down")
    void analyze_appliesTrendMultiplierDown() {
        FixtureContext context = createCornersWithDownwardTrend();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("trendDirection")).isEqualTo("DOWN");
        assertThat(result.get().getDescription()).contains("trending down");
    }

    @Test
    @DisplayName("analyze tracks corners conceded")
    void analyze_tracksCornersConceeded() {
        FixtureContext context = createHighCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeConcededAvg");
        assertThat(result.get().getFactors()).containsKey("awayConcededAvg");
    }

    @Test
    @DisplayName("analyze tracks playing style multiplier")
    void analyze_tracksPlayingStyleMultiplier() {
        FixtureContext context = createHighCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("playingStyleMultiplier");
        assertThat(result.get().getFactors()).containsKey("matchContextMultiplier");
    }

    @Test
    @DisplayName("analyze tracks position difference")
    void analyze_tracksPositionDifference() {
        FixtureContext context = createHighCornersContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homePosition");
        assertThat(result.get().getFactors()).containsKey("awayPosition");
        assertThat(result.get().getFactors()).containsKey("positionDifference");
    }

    @Test
    @DisplayName("analyze works without form data")
    void analyze_withoutFormData_stillWorks() {
        FixtureContext context = createHighCornersContextNoForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDataAvailable")).isEqualTo(false);
    }

    // Helper methods

    private FixtureContext createHighCornersContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(7.5)
                .cornersAvgAway(5.5)
                .cornersAvgOverall(6.5)
                .seasonGoalsHome(25)
                .seasonGoalsAway(15)
                .position(3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(6.0)
                .cornersAvgAway(6.5)
                .cornersAvgOverall(6.2)
                .seasonGoalsHome(20)
                .seasonGoalsAway(12)
                .position(5)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(8.0)
                .cornersAvgAway(6.0)
                .cornersAvgOverall(7.0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(6.5)
                .cornersAvgAway(7.0)
                .cornersAvgOverall(6.7)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "High Corners Home"))
                .awayTeam(createTeam(2L, "High Corners Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createHighCornersContextNoForm() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(7.5)
                .cornersAvgAway(5.5)
                .cornersAvgOverall(6.5)
                .seasonGoalsHome(25)
                .position(3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(6.0)
                .cornersAvgAway(6.5)
                .cornersAvgOverall(6.2)
                .seasonGoalsAway(12)
                .position(5)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "High Corners Home"))
                .awayTeam(createTeam(2L, "High Corners Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createLowCornersContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(3.5)
                .cornersAvgAway(3.0)
                .cornersAvgOverall(3.2)
                .seasonGoalsHome(12)
                .position(15)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(3.0)
                .cornersAvgAway(3.5)
                .cornersAvgOverall(3.2)
                .seasonGoalsAway(8)
                .position(18)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(3.2)
                .cornersAvgAway(2.8)
                .cornersAvgOverall(3.0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(2.8)
                .cornersAvgAway(3.0)
                .cornersAvgOverall(2.9)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Low Corners Home"))
                .awayTeam(createTeam(2L, "Low Corners Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createAverageCornersContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(5.0)
                .cornersAvgAway(4.5)
                .cornersAvgOverall(4.7)
                .seasonGoalsHome(15)
                .position(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(4.5)
                .cornersAvgAway(5.0)
                .cornersAvgOverall(4.7)
                .seasonGoalsAway(10)
                .position(12)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(5.0)
                .cornersAvgAway(4.5)
                .cornersAvgOverall(4.7)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(4.5)
                .cornersAvgAway(5.0)
                .cornersAvgOverall(4.7)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Average Home"))
                .awayTeam(createTeam(2L, "Average Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createHighCornersContextWithPotentials() {
        FixtureContext base = createHighCornersContext();
        
        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cornersPotential(75.0)
                .cornersO85Potential(70.0)
                .cornersO95Potential(65.0)
                .cornersO105Potential(55.0)
                .build();

        return FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .homeTeamForm(base.getHomeTeamForm())
                .awayTeamForm(base.getAwayTeamForm())
                .potentials(potentials)
                .build();
    }

    private FixtureContext createModerateCornersWithHighApiPotential() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(5.5)
                .cornersAvgAway(5.0)
                .cornersAvgOverall(5.2)
                .seasonGoalsHome(18)
                .position(7)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(5.0)
                .cornersAvgAway(5.5)
                .cornersAvgOverall(5.2)
                .seasonGoalsAway(12)
                .position(9)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(6.0)
                .cornersAvgAway(5.5)
                .cornersAvgOverall(5.7)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(5.5)
                .cornersAvgAway(6.0)
                .cornersAvgOverall(5.7)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cornersPotential(80.0)
                .cornersO85Potential(75.0)
                .cornersO95Potential(70.0)
                .cornersO105Potential(72.0)  // High API potential
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Moderate Home"))
                .awayTeam(createTeam(2L, "Moderate Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createCornersWithUpwardTrend() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(5.0)  // Season avg
                .cornersAvgAway(4.5)
                .cornersAvgOverall(4.7)
                .seasonGoalsHome(18)
                .position(6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(4.5)
                .cornersAvgAway(5.0)  // Season avg
                .cornersAvgOverall(4.7)
                .seasonGoalsAway(12)
                .position(8)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(6.5)  // Much higher than season (trending up)
                .cornersAvgAway(5.5)
                .cornersAvgOverall(6.0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(5.5)
                .cornersAvgAway(6.5)  // Much higher than season
                .cornersAvgOverall(6.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Trending Up Home"))
                .awayTeam(createTeam(2L, "Trending Up Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createCornersWithDownwardTrend() {
        // Start with high season corners, trending down but still producing a recommendation
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(7.0)  // High season avg
                .cornersAvgAway(6.0)
                .cornersAvgOverall(6.5)
                .seasonGoalsHome(18)
                .position(6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .cornersAvgHome(6.0)
                .cornersAvgAway(7.0)  // High season avg
                .cornersAvgOverall(6.5)
                .seasonGoalsAway(12)
                .position(8)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cornersAvgHome(5.5)  // Lower than season (trending down, but still results in Over 9.5)
                .cornersAvgAway(5.0)
                .cornersAvgOverall(5.2)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cornersAvgHome(5.0)
                .cornersAvgAway(5.5)  // Lower than season
                .cornersAvgOverall(5.2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Trending Down Home"))
                .awayTeam(createTeam(2L, "Trending Down Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private Fixture createFixture() {
        return Fixture.builder()
                .id(1000L)
                .seasonId(100L)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .homeTeamName("Home Team")
                .awayTeamName("Away Team")
                .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                .status("incomplete")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
