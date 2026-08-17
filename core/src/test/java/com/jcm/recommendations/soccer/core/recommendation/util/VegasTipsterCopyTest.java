package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VegasTipsterCopyTest {

    @Test
    @DisplayName("narrate wraps real numbers in tipster voice with showtime")
    void narrateIncludesNumbersAndShowtime() {
        FixtureContext context = FixtureContext.builder()
                .fixture(Fixture.builder().id(42L).build())
                .homeTeam(Team.builder().id(1L).name("Home FC").build())
                .awayTeam(Team.builder().id(2L).name("Away United").build())
                .build();

        String text = VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(ConfidenceLevel.STRONG)
                .selection("Home Win")
                .context(context)
                .probabilityPct(84.3)
                .valuePct(11.4)
                .colourNote("Team in good form")
                .build());

        assertThat(text).contains("Home Win");
        assertThat(text).contains("Strong lean");
        assertThat(text).contains("84.3%");
        assertThat(text).contains("+11.4%");
        assertThat(text).contains("Team in good form");
        assertThat(text).contains("Showtime: Home FC vs Away United.");
        assertThat(text.toLowerCase()).doesNotContain("lock");
        assertThat(text.toLowerCase()).doesNotContain("guaranteed");
        assertThat(text.toLowerCase()).doesNotContain("mortgage");
    }

    @Test
    @DisplayName("narrate can describe expected totals without probability")
    void narrateExpectedTotals() {
        FixtureContext context = FixtureContext.builder()
                .fixture(Fixture.builder().id(7L).build())
                .homeTeam(Team.builder().id(1L).name("A").build())
                .awayTeam(Team.builder().id(2L).name("B").build())
                .build();

        String text = VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(ConfidenceLevel.MODERATE)
                .selection("Over 9.5 Corners")
                .context(context)
                .expected(10.4, "expected corners")
                .colourNote("API potential boost")
                .build());

        assertThat(text).contains("Over 9.5 Corners");
        assertThat(text).contains("Moderate lean");
        assertThat(text).contains("10.4 expected corners");
        assertThat(text).contains("API potential boost");
        assertThat(text).contains("Showtime: A vs B.");
    }
}
