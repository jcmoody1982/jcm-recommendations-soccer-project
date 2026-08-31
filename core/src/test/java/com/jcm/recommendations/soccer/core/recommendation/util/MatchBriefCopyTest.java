package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MatchBriefCopyTest {

    /**
     * Banned as whole words. Substring matching would flag innocent copy such as
     * "clocks" (contains "lock") or "rapid" (contains "api").
     */
    private static final String[] BANNED_WORDS = {
            "lock", "locks", "guaranteed", "mortgage", "api", "chips", "juice"
    };

    private static void assertNoHypeLanguage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : BANNED_WORDS) {
            assertThat(lower).doesNotContainPattern("\\b" + word + "\\b");
        }
        assertThat(lower).doesNotContain("the model");
    }

    @Test
    @DisplayName("narrate keeps real numbers and fixture names without certainty language")
    void narrateIncludesNumbersAndFixture() {
        FixtureContext context = context(42L, "Home FC", "Away United");

        String text = MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
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
        // Closers vary ("vs", "against", "host"), so assert both sides are named
        // rather than pinning one separator.
        assertThat(text).contains("Home FC");
        assertThat(text).contains("Away United");
        assertNoHypeLanguage(text);
    }

    @Test
    @DisplayName("narrate can describe expected totals without probability")
    void narrateExpectedTotals() {
        FixtureContext context = context(7L, "Corner City", "Set Piece Rovers");

        String text = MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(ConfidenceLevel.MODERATE)
                .selection("Over 9.5 Corners")
                .context(context)
                .expected(10.4, "expected corners")
                .colourNote("Corner potential reading is strong on the analysis")
                .build());

        assertThat(text).contains("Over 9.5 Corners");
        assertThat(text).contains("Moderate");
        assertThat(text).contains("10.4");
        assertThat(text).contains("expected corners");
        assertThat(text).contains("Corner potential reading is strong on the analysis");
        assertThat(text).contains("Corner City");
        assertThat(text).contains("Set Piece Rovers");
        assertNoHypeLanguage(text);
    }

    @Test
    @DisplayName("narrate varies layouts and phrasing across fixtures")
    void narrateVariesAcrossFixtures() {
        Set<String> outputs = new HashSet<>();
        String[] selections = {"Home Win", "BTTS Yes", "Over 2.5 Goals", "Draw", "Over 9.5 Corners"};
        for (long fixtureId = 1; fixtureId <= 40; fixtureId++) {
            String text = MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                    .confidence(fixtureId % 3 == 0 ? ConfidenceLevel.STRONG
                            : fixtureId % 3 == 1 ? ConfidenceLevel.MODERATE : ConfidenceLevel.WEAK)
                    .selection(selections[(int) (fixtureId % selections.length)])
                    .context(context(fixtureId, "Home FC", "Away United"))
                    .probabilityPct(55.0 + fixtureId)
                    .valuePct(5.0 + fixtureId / 5.0)
                    .colourNote(fixtureId % 2 == 0 ? "Team in good form" : null)
                    .build());
            assertNoHypeLanguage(text);
            outputs.add(text);
        }
        assertThat(outputs.size()).isGreaterThan(20);
    }

    @Test
    @DisplayName("narration is pinned so identity-based hashing cannot creep back in")
    void narrateIsStableAcrossJvmRuns() {
        // Template choice must derive only from brief values. Hashing an enum
        // directly uses its identity hash, which shifts between JVM runs and
        // silently reworded the same tip on every restart. Update this string
        // only when the copy itself is deliberately changed.
        String text = MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(ConfidenceLevel.STRONG)
                .selection("Home Win")
                .context(context(2024L, "Anchor FC", "Ballast Town"))
                .probabilityPct(71.5)
                .build());

        assertThat(text).isEqualTo(
                "The work puts it at 71.5%. Mark Home Win on the card. "
                        + "Strong mark \u2014 the analysis is clear. "
                        + "The fixture reads Anchor FC vs Ballast Town.");
    }

    @Test
    @DisplayName("same brief narrates stably")
    void narrateIsStableForSameBrief() {
        FixtureContext context = context(99L, "Alpha", "Beta");
        var brief = MatchBriefCopy.Brief.builder()
                .confidence(ConfidenceLevel.STRONG)
                .selection("Draw")
                .context(context)
                .probabilityPct(41.2)
                .build();

        assertThat(MatchBriefCopy.narrate(brief)).isEqualTo(MatchBriefCopy.narrate(brief));
    }

    private static FixtureContext context(long fixtureId, String home, String away) {
        return FixtureContext.builder()
                .fixture(Fixture.builder().id(fixtureId).build())
                .homeTeam(Team.builder().id(1L).name(home).build())
                .awayTeam(Team.builder().id(2L).name(away).build())
                .build();
    }
}
