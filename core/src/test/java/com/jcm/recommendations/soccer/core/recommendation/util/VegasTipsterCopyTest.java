package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VegasTipsterCopyTest {

    @Test
    @DisplayName("narrate keeps real numbers and fixture names without certainty language")
    void narrateIncludesNumbersAndFixture() {
        FixtureContext context = context(42L, "Home FC", "Away United");

        String text = VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(ConfidenceLevel.STRONG)
                .selection("Home Win")
                .context(context)
                .probabilityPct(84.3)
                .valuePct(11.4)
                .colourNote("Team in good form")
                .build());

        assertThat(text).contains("Home Win");
        assertThat(text).contains("Strong");
        assertThat(text).contains("84.3%");
        assertThat(text).contains("+11.4%");
        assertThat(text).contains("Team in good form");
        assertThat(text).contains("Home FC");
        assertThat(text).contains("Away United");
        assertThat(text).contains("vs");
        assertThat(text.toLowerCase()).doesNotContain("lock");
        assertThat(text.toLowerCase()).doesNotContain("guaranteed");
        assertThat(text.toLowerCase()).doesNotContain("mortgage");
    }

    @Test
    @DisplayName("narrate can describe expected totals without probability")
    void narrateExpectedTotals() {
        FixtureContext context = context(7L, "A", "B");

        String text = VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(ConfidenceLevel.MODERATE)
                .selection("Over 9.5 Corners")
                .context(context)
                .expected(10.4, "expected corners")
                .colourNote("API potential boost")
                .build());

        assertThat(text).contains("Over 9.5 Corners");
        assertThat(text).contains("Moderate");
        assertThat(text).contains("10.4");
        assertThat(text).contains("expected corners");
        assertThat(text).contains("API potential boost");
        assertThat(text).contains("A");
        assertThat(text).contains("B");
        assertThat(text).contains("vs");
    }

    @Test
    @DisplayName("narrate varies layouts and phrasing across fixtures")
    void narrateVariesAcrossFixtures() {
        Set<String> outputs = new HashSet<>();
        String[] selections = {"Home Win", "BTTS Yes", "Over 2.5 Goals", "Draw", "Over 9.5 Corners"};
        for (long fixtureId = 1; fixtureId <= 40; fixtureId++) {
            String text = VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                    .confidence(fixtureId % 3 == 0 ? ConfidenceLevel.STRONG
                            : fixtureId % 3 == 1 ? ConfidenceLevel.MODERATE : ConfidenceLevel.WEAK)
                    .selection(selections[(int) (fixtureId % selections.length)])
                    .context(context(fixtureId, "Home FC", "Away United"))
                    .probabilityPct(55.0 + fixtureId)
                    .valuePct(5.0 + fixtureId / 5.0)
                    .colourNote(fixtureId % 2 == 0 ? "Team in good form" : null)
                    .build());
            outputs.add(text);
        }
        assertThat(outputs.size()).isGreaterThan(20);
    }

    @Test
    @DisplayName("same brief narrates stably")
    void narrateIsStableForSameBrief() {
        FixtureContext context = context(99L, "Alpha", "Beta");
        var brief = VegasTipsterCopy.Brief.builder()
                .confidence(ConfidenceLevel.STRONG)
                .selection("Draw")
                .context(context)
                .probabilityPct(41.2)
                .build();

        assertThat(VegasTipsterCopy.narrate(brief)).isEqualTo(VegasTipsterCopy.narrate(brief));
    }

    private static FixtureContext context(long fixtureId, String home, String away) {
        return FixtureContext.builder()
                .fixture(Fixture.builder().id(fixtureId).build())
                .homeTeam(Team.builder().id(1L).name(home).build())
                .awayTeam(Team.builder().id(2L).name(away).build())
                .build();
    }
}
