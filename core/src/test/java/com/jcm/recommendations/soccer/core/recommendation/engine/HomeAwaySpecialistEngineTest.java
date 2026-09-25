package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("analyze publishes priced home tip when specialist + poor traveler + odds agree")
    void analyze_dualConfirmationWithOdds_publishesHome() {
        Optional<Recommendation> result = engine.analyze(dualConfirmationContext(1.85, true));

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.HOME_AWAY_SPECIALIST);
        assertThat(result.get().getMarket()).isEqualTo("Home Team");
        assertThat(result.get().getOdds()).isEqualTo(1.85);
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
        assertThat(result.get().getFactors().get("dualConfirmationRequired")).isEqualTo(true);
        assertThat(result.get().getFactors().get("poorTraveler")).isEqualTo(true);
        assertThat(result.get().getFactors().get("marketBlendApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("classification").toString()).contains("Poor Traveler");
        assertThat(result.get().getScore()).isGreaterThanOrEqualTo(55.0).isLessThan(75.0);
    }

    @Test
    @DisplayName("analyze requires stakeable home odds")
    void analyze_requiresStakeableOdds() {
        assertThat(engine.analyze(dualConfirmationContext(1.30, true))).isEmpty();
        assertThat(engine.analyze(dualConfirmationContext(2.80, true))).isEmpty();
        assertThat(engine.analyze(dualConfirmationContext(null, true))).isEmpty();
    }

    @Test
    @DisplayName("analyze requires poor traveler dual confirmation")
    void analyze_withoutPoorTraveler_isEmpty() {
        assertThat(engine.analyze(dualConfirmationContext(1.85, false))).isEmpty();
    }

    @Test
    @DisplayName("analyze skips declining home form")
    void analyze_decliningHomeForm_isEmpty() {
        FixtureContext context = dualConfirmationBuilder(1.85, true)
                .homeTeamForm(TeamRecentForm.builder()
                        .teamId(1L)
                        .ppgHome(1.2)
                        .build())
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze skips away specialist paths")
    void analyze_awaySpecialistOnly_isEmpty() {
        TeamSeasonStats weakHome = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .ppgHome(1.1)
                .ppgAway(1.0)
                .seasonWinsHome(4)
                .seasonDrawsHome(6)
                .seasonLossesHome(10)
                .seasonWinsAway(4)
                .seasonDrawsAway(5)
                .seasonLossesAway(11)
                .seasonGoalsHome(16)
                .seasonGoalsAway(14)
                .seasonConcededHome(24)
                .seasonConcededAway(26)
                .build();
        TeamSeasonStats strongAway = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .ppgHome(1.2)
                .ppgAway(2.1)
                .seasonWinsHome(5)
                .seasonDrawsHome(5)
                .seasonLossesHome(10)
                .seasonWinsAway(12)
                .seasonDrawsAway(4)
                .seasonLossesAway(4)
                .seasonGoalsHome(18)
                .seasonGoalsAway(28)
                .seasonConcededHome(22)
                .seasonConcededAway(14)
                .build();

        assertThat(engine.analyze(FixtureContext.builder()
                .fixture(createFixture(200L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(weakHome)
                .awayTeamStats(strongAway)
                .odds(FixtureOdds.builder().fixtureId(200L).oddsFt1(1.90).build())
                .build())).isEmpty();
    }

    @Test
    @DisplayName("STRONG requires xG confirmation and shorter odds")
    void analyze_strongRequiresXgAndShortEnoughOdds() {
        Optional<Recommendation> withXg = engine.analyze(dualConfirmationContext(1.70, true));
        assertThat(withXg).isPresent();
        assertThat(withXg.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);

        Optional<Recommendation> longOdds = engine.analyze(dualConfirmationContext(2.10, true));
        assertThat(longOdds).isPresent();
        assertThat(longOdds.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
    }

    private FixtureContext dualConfirmationContext(Double homeOdds, boolean poorTraveler) {
        return dualConfirmationBuilder(homeOdds, poorTraveler).build();
    }

    private FixtureContext.FixtureContextBuilder dualConfirmationBuilder(
            Double homeOdds, boolean poorTraveler) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(2.3)
                .ppgAway(1.2)
                .seasonWinsHome(14)
                .seasonDrawsHome(4)
                .seasonLossesHome(2)
                .seasonWinsAway(5)
                .seasonDrawsAway(5)
                .seasonLossesAway(10)
                .seasonGoalsHome(35)
                .seasonGoalsAway(16)
                .seasonConcededHome(12)
                .seasonConcededAway(26)
                .xgForAvgHome(1.9)
                .xgForAvgAway(1.0)
                .build();

        TeamSeasonStats.TeamSeasonStatsBuilder awayBuilder = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .seasonWinsHome(7)
                .seasonDrawsHome(6)
                .seasonLossesHome(7)
                .seasonGoalsHome(20)
                .seasonConcededHome(22)
                .seasonConcededAway(28);

        if (poorTraveler) {
            awayBuilder
                    .ppgAway(0.70)
                    .seasonWinsAway(2)
                    .seasonDrawsAway(5)
                    .seasonLossesAway(13)
                    .seasonGoalsAway(12);
        } else {
            awayBuilder
                    .ppgAway(1.40)
                    .seasonWinsAway(7)
                    .seasonDrawsAway(5)
                    .seasonLossesAway(8)
                    .seasonGoalsAway(18);
        }

        FixtureContext.FixtureContextBuilder builder = FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayBuilder.build())
                .homeTeamForm(TeamRecentForm.builder()
                        .teamId(1L)
                        .ppgHome(2.4)
                        .build());

        if (homeOdds != null) {
            builder.odds(FixtureOdds.builder()
                    .fixtureId(101L)
                    .oddsFt1(homeOdds)
                    .oddsFtX(3.50)
                    .oddsFt2(4.50)
                    .build());
        }
        return builder;
    }

    private Fixture createFixture(long id) {
        return Fixture.builder()
                .id(id)
                .seasonId(1L)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .homeTeamName("Home Team")
                .awayTeamName("Away Team")
                .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                .status("incomplete")
                .build();
    }

    private Team createTeam(long id, String name) {
        return Team.builder().id(id).name(name).build();
    }
}
