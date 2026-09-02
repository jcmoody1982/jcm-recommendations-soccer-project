package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Guards the FootyStats key names against a captured {@code /league-teams?include=stats} response.
 *
 * <p>A mistyped key does not fail deserialisation, it just leaves the field null, and the engines
 * then quietly substitute a hard-coded default. That failure mode is invisible in production, so
 * the mapping has to be pinned to a real payload rather than to hand-written JSON.
 *
 * <p>Fixture is Manchester City's 2019/20 season: 38 played, 102 scored (57 home, 45 away),
 * 26W 3D 9L, finished 2nd.
 */
class TeamStatsDtoMappingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static TeamStatsDto stats;

    @BeforeAll
    static void loadCapturedPayload() throws Exception {
        try (InputStream json = TeamStatsDtoMappingTest.class
                .getResourceAsStream("/footystats/league-teams-stats.json")) {
            assertThat(json).as("captured league-teams fixture").isNotNull();
            ApiResponse<TeamDto> response = OBJECT_MAPPER.readValue(
                    json, new TypeReference<ApiResponse<TeamDto>>() {});
            stats = response.getData().get(0).getStats();
        }
    }

    @Test
    void everyMappedFieldResolvesAgainstARealPayload() {
        List<String> unresolved = new ArrayList<>();
        for (Field field : TeamStatsDto.class.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            // Rebuilt from wins and draws rather than read from the feed.
            if ("points".equals(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            try {
                if (field.get(stats) == null) {
                    unresolved.add(field.getName() + " (" + jsonKeysOf(field) + ")");
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        assertThat(unresolved)
                .as("fields left null by the real payload — the JSON key is wrong")
                .isEmpty();
    }

    @Test
    void readsMatchesPlayedOverallAndPerVenue() {
        assertThat(stats.getMatchesPlayed()).isEqualTo(38);
        assertThat(stats.getMatchesPlayedHome()).isEqualTo(19);
        assertThat(stats.getMatchesPlayedAway()).isEqualTo(19);
    }

    @Test
    void readsGoalsScoredAsVenueTotalsRatherThanGoalMinuteLists() {
        assertThat(stats.getSeasonGoalsHome()).isEqualTo(57);
        assertThat(stats.getSeasonGoalsAway()).isEqualTo(45);
        assertThat(stats.getSeasonGoalsOverall())
                .isEqualTo(stats.getSeasonGoalsHome() + stats.getSeasonGoalsAway());
    }

    @Test
    void venueGoalsDividedByVenueMatchesAgreeWithTheFeedsOwnAverage() {
        double computedHome = stats.getSeasonGoalsHome() / (double) stats.getMatchesPlayedHome();
        double computedAway = stats.getSeasonGoalsAway() / (double) stats.getMatchesPlayedAway();

        assertThat(computedHome).isCloseTo(stats.getScoredAvgHome(), within(0.01));
        assertThat(computedAway).isCloseTo(stats.getScoredAvgAway(), within(0.01));
    }

    @Test
    void dividingVenueGoalsByOverallMatchesWouldHalveTheAverage() {
        double wrong = stats.getSeasonGoalsHome() / (double) stats.getMatchesPlayed();

        assertThat(wrong).isLessThan(stats.getScoredAvgHome() * 0.6);
    }

    @Test
    void readsOverLineCountsNotPercentages() {
        assertThat(stats.getSeasonOver15Overall()).isLessThanOrEqualTo(stats.getMatchesPlayed());
        assertThat(stats.getSeasonOver25Overall()).isLessThanOrEqualTo(stats.getSeasonOver15Overall());
        assertThat(stats.getSeasonOver35Overall()).isLessThanOrEqualTo(stats.getSeasonOver25Overall());
    }

    @Test
    void readsLeaguePosition() {
        assertThat(stats.getPosition()).isEqualTo(2);
    }

    @Test
    void rebuildsSeasonPointsFromResultsBecauseTheFeedOmitsThem() {
        assertThat(stats.getPoints()).isEqualTo(81);
    }

    @Test
    void prefersAnExplicitPointsTotalWhenTheFeedEverStartsSendingOne() {
        TeamStatsDto explicit = new TeamStatsDto();
        explicit.setSeasonWinsOverall(26);
        explicit.setSeasonDrawsOverall(3);
        explicit.setPoints(70);

        assertThat(explicit.getPoints()).isEqualTo(70);
    }

    @Test
    void reportsNoPointsWhenResultsAreMissing() {
        assertThat(new TeamStatsDto().getPoints()).isNull();
    }

    private static String jsonKeysOf(Field field) {
        List<String> keys = new ArrayList<>();
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        if (property != null) {
            keys.add(property.value());
        }
        JsonAlias alias = field.getAnnotation(JsonAlias.class);
        if (alias != null) {
            keys.addAll(Arrays.asList(alias.value()));
        }
        return keys.isEmpty() ? field.getName() : String.join(" | ", keys);
    }
}
