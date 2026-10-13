package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerToScoreRecommendationEngineTest {

    /** Goals the away side concedes on the road over 9 matches; 15 is this league's norm. */
    private static final int NEUTRAL_AWAY_CONCEDED = 15;
    private static final int LEAKY_AWAY_CONCEDED = 27;

    private PlayerToScoreRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PlayerToScoreRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns PLAYER_TO_SCORE")
    void getType_returnsPlayerToScore() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.PLAYER_TO_SCORE);
    }

    @Test
    @DisplayName("tracks the likeliest scorer across both squads as an unpriced Moderate shadow pick")
    void analyze_picksBestScorerAcrossSquads() {
        FixtureContext context = context(NEUTRAL_AWAY_CONCEDED,
                List.of(striker(10L, "Home Striker", 10, 1, 1500, 17, 88)),
                List.of(striker(20L, "Away Forward", 5, 0, 1400, 16, 80)));

        Recommendation rec = engine.analyze(context).orElseThrow();

        assertThat(rec.getMarket()).isEqualTo("Home Striker to score");
        assertThat(rec.getOdds()).isNull();
        assertThat(rec.getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(rec.getScore()).isBetween(35.0, 42.0);
        assertThat(rec.getFactors())
                .containsEntry("playerId", 10L)
                .containsEntry("isHome", true)
                .containsEntry("shadow", true)
                .containsEntry("meetsPublishBar", true)
                .containsKeys("ratePer90", "teamExpectedGoals", "fixtureFactor", "availability", "expectedEvents");
    }

    @Test
    @DisplayName("an ordinary forward is tracked but flagged as below the publish bar")
    void analyze_averageForward_isTrackedBelowBar() {
        Recommendation rec = engine.analyze(context(NEUTRAL_AWAY_CONCEDED,
                List.of(striker(10L, "Squad Forward", 5, 0, 1400, 16, 80)), List.of())).orElseThrow();

        assertThat(rec.getScore()).isBetween(20.0, 25.0);
        assertThat(rec.getFactors()).containsEntry("meetsPublishBar", false);
    }

    @Test
    @DisplayName("nothing is tracked when the best candidate is under the tracking floor")
    void analyze_belowTrackingFloor_isEmpty() {
        assertThat(engine.analyze(context(NEUTRAL_AWAY_CONCEDED,
                List.of(striker(10L, "Holding Midfielder", 2, 0, 1200, 16, 80)), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("a leaky opponent lifts the same striker's probability through team expected goals")
    void analyze_fixtureContextMovesTheScore() {
        PlayerSeasonStats striker = striker(10L, "Home Striker", 10, 1, 1500, 17, 88);

        double neutral = engine.analyze(context(NEUTRAL_AWAY_CONCEDED, List.of(striker), List.of()))
                .orElseThrow().getScore();
        double leaky = engine.analyze(context(LEAKY_AWAY_CONCEDED, List.of(striker), List.of()))
                .orElseThrow().getScore();

        assertThat(leaky).isGreaterThan(neutral + 5.0);
    }

    @Test
    @DisplayName("a player who misses matches is discounted for availability")
    void analyze_availabilityDiscountsIrregulars() {
        double regular = engine.analyze(context(NEUTRAL_AWAY_CONCEDED,
                List.of(striker(10L, "Ever Present", 10, 1, 1500, 18, 88)), List.of())).orElseThrow().getScore();
        double irregular = engine.analyze(context(NEUTRAL_AWAY_CONCEDED,
                List.of(striker(10L, "Often Absent", 10, 1, 1500, 12, 88)), List.of())).orElseThrow().getScore();

        assertThat(irregular).isLessThan(regular * 0.75);
    }

    @Test
    @DisplayName("penalties are credited in full rather than shrunk toward the prior")
    void ratePer90_penaltiesAreNotShrunk() {
        double openPlayOnly = engine.ratePer90(striker(10L, "Open Play", 10, 0, 1500, 17, 88));
        double penaltyTaker = engine.ratePer90(striker(11L, "Penalty Taker", 10, 4, 1500, 17, 88));

        assertThat(penaltyTaker).isGreaterThan(openPlayOnly);
    }

    @Test
    @DisplayName("a hot home-only split no longer drives the pick")
    void analyze_ignoresVenueSplits() {
        PlayerSeasonStats plain = striker(10L, "Home Striker", 10, 1, 1500, 17, 88);
        PlayerSeasonStats hotAtHome = striker(10L, "Home Striker", 10, 1, 1500, 17, 88);
        hotAtHome.setGoalsPer90Home(1.5);

        double plainScore = engine.analyze(context(NEUTRAL_AWAY_CONCEDED, List.of(plain), List.of()))
                .orElseThrow().getScore();
        double splitScore = engine.analyze(context(NEUTRAL_AWAY_CONCEDED, List.of(hotAtHome), List.of()))
                .orElseThrow().getScore();

        assertThat(splitScore).isEqualTo(plainScore);
    }

    @Test
    @DisplayName("filters drop keepers, thin samples, part-timers and nameless players")
    void analyze_filters() {
        PlayerSeasonStats keeper = striker(1L, "Keeper", 10, 1, 1500, 17, 90);
        keeper.setPosition("Goalkeeper");
        PlayerSeasonStats thin = striker(2L, "Thin Sample", 4, 0, 500, 6, 85);
        PlayerSeasonStats partTimer = striker(3L, "Impact Sub", 8, 0, 900, 17, 55);
        PlayerSeasonStats nameless = striker(4L, null, 10, 1, 1500, 17, 88);
        PlayerSeasonStats noId = striker(null, "No Id", 10, 1, 1500, 17, 88);

        assertThat(engine.analyze(context(NEUTRAL_AWAY_CONCEDED,
                Arrays.asList(keeper, thin, null), List.of(partTimer, nameless, noId)))).isEmpty();
    }

    @Test
    @DisplayName("not applicable without player stats")
    void analyze_withoutPlayers_isEmpty() {
        assertThat(engine.analyze(context(NEUTRAL_AWAY_CONCEDED, null, null))).isEmpty();
    }

    static FixtureContext context(int awayConcededAway, List<PlayerSeasonStats> home, List<PlayerSeasonStats> away) {
        return FixtureContext.builder()
                .fixture(Fixture.builder()
                        .id(1000L)
                        .seasonId(100L)
                        .homeTeamId(1L)
                        .awayTeamId(2L)
                        .homeTeamName("Home Team")
                        .awayTeamName("Away Team")
                        .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                        .status("incomplete")
                        .build())
                .homeTeam(Team.builder().id(1L).name("Home Team").build())
                .awayTeam(Team.builder().id(2L).name("Away Team").build())
                .homeTeamStats(team(1L, 15))
                .awayTeamStats(team(2L, awayConcededAway))
                .homePlayers(home)
                .awayPlayers(away)
                .build();
    }

    /** 18 matches (9 home, 9 away), 26 goals scored, 15 conceded at home. */
    private static TeamSeasonStats team(long id, int concededAway) {
        return TeamSeasonStats.builder()
                .teamId(id)
                .seasonId(100L)
                .matchesPlayed(18)
                .matchesPlayedHome(9)
                .matchesPlayedAway(9)
                .seasonGoalsOverall(26)
                .seasonGoalsHome(15)
                .seasonGoalsAway(11)
                .seasonConcededHome(11)
                .seasonConcededAway(concededAway)
                .build();
    }

    private static PlayerSeasonStats striker(Long id, String knownAs, int goals, int penalties,
            int minutes, int appearances, int minPerMatch) {
        return PlayerSeasonStats.builder()
                .playerId(id)
                .seasonId(100L)
                .knownAs(knownAs)
                .position("Forward")
                .appearancesOverall(appearances)
                .minutesPlayedOverall(minutes)
                .minPerMatch(minPerMatch)
                .goalsOverall(goals)
                .penaltyGoals(penalties)
                .build();
    }
}
