package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.League;
import com.jcm.recommendations.soccer.domain.Team;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationFactoryTest {

    private static FixtureContext context(League league) {
        return FixtureContext.builder()
                .fixture(Fixture.builder().id(1000L).dateUnix(1_800_000_000L).build())
                .homeTeam(Team.builder().id(1L).name("Arsenal").build())
                .awayTeam(Team.builder().id(2L).name("Chelsea").build())
                .league(league)
                .build();
    }

    @Test
    void fromContextCopiesFixtureTeamsAndLeague() {
        League league = League.builder().currentSeasonId(12325L).name("England Premier League").image("epl.png").build();

        Recommendation rec = RecommendationFactory.fromContext(context(league)).build();

        assertThat(rec.getFixtureId()).isEqualTo(1000L);
        assertThat(rec.getHomeTeamId()).isEqualTo(1L);
        assertThat(rec.getAwayTeamName()).isEqualTo("Chelsea");
        assertThat(rec.getMatchDateUnix()).isEqualTo(1_800_000_000L);
        assertThat(rec.getLeagueId()).isEqualTo(12325L);
        assertThat(rec.getLeagueName()).isEqualTo("England Premier League");
        assertThat(rec.getLeagueImage()).isEqualTo("epl.png");
        assertThat(rec.getGeneratedAt()).isNotNull();
    }

    @Test
    void fromContextToleratesMissingLeague() {
        Recommendation rec = RecommendationFactory.fromContext(context(null)).build();

        assertThat(rec.getLeagueId()).isNull();
        assertThat(rec.getLeagueName()).isNull();
        assertThat(rec.getLeagueImage()).isNull();
    }

    @Test
    void createSetsEveryPickField() {
        Recommendation rec = RecommendationFactory.create(context(null), RecommendationType.BTTS,
                ConfidenceLevel.STRONG, 81.5, "Both teams to score", 1.85, "desc", Map.of("k", 1));

        assertThat(rec.getType()).isEqualTo(RecommendationType.BTTS);
        assertThat(rec.getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
        assertThat(rec.getScore()).isEqualTo(81.5);
        assertThat(rec.getMarket()).isEqualTo("Both teams to score");
        assertThat(rec.getOdds()).isEqualTo(1.85);
        assertThat(rec.getDescription()).isEqualTo("desc");
        assertThat(rec.getFactors()).containsEntry("k", 1);
    }

    @Test
    void descriptionHelpersProduceNonBlankCopy() {
        FixtureContext ctx = context(null);

        assertThat(RecommendationFactory.buildStandardDescription(ConfidenceLevel.STRONG, "Arsenal to win", 72, "score", ctx))
                .isNotBlank();
        assertThat(RecommendationFactory.buildExpectedValueDescription(ConfidenceLevel.MODERATE, "Over 2.5 Goals", 3.1, "expected goals", ctx))
                .isNotBlank();
        assertThat(RecommendationFactory.buildTeamDescription(ConfidenceLevel.STRONG, "Clean sheet", "Arsenal", 65, ctx))
                .isNotBlank();
    }
}
