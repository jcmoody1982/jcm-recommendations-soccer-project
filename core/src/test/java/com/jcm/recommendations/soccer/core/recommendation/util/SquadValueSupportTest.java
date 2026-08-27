package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SquadValueSupportTest {

    @Test
    void formatDifference_formatsHomeAdvantage() {
        FixtureContext context = contextWithValues(900_000_000L, 200_000_000L);

        assertThat(SquadValueSupport.formatDifference(context))
                .isEqualTo("Home €900m vs Away €200m (home 4.5×)");
        assertThat(SquadValueSupport.colourNote(context))
                .startsWith("Squad value backs the home side");
    }

    @Test
    void putFactors_includesReadableDifference() {
        FixtureContext context = contextWithValues(900_000_000L, 200_000_000L);
        Map<String, Object> factors = new HashMap<>();

        SquadValueSupport.putFactors(context, factors);

        assertThat(factors.get("squadValueDifference"))
                .isEqualTo("Home €900m vs Away €200m (home 4.5×)");
        assertThat(factors.get("squadValueDifferenceEur")).isEqualTo(700_000_000L);
    }

    @Test
    void appendColourNote_appendsWhenPresent() {
        FixtureContext context = contextWithValues(900_000_000L, 200_000_000L);

        assertThat(SquadValueSupport.appendColourNote("Clear gulf in standing", context))
                .contains("Clear gulf in standing")
                .contains("Squad value backs the home side")
                .contains("Home €900m vs Away €200m");
    }

    private static FixtureContext contextWithValues(long homeEur, long awayEur) {
        return FixtureContext.builder()
                .homeSquadProfile(TeamSquadProfile.builder()
                        .teamId(1L)
                        .totalMarketValueEur(homeEur)
                        .engineUsable(true)
                        .build())
                .awaySquadProfile(TeamSquadProfile.builder()
                        .teamId(2L)
                        .totalMarketValueEur(awayEur)
                        .engineUsable(true)
                        .build())
                .build();
    }
}
