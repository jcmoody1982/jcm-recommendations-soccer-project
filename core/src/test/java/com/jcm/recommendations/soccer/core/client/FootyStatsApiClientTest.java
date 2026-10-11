package com.jcm.recommendations.soccer.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jcm.recommendations.soccer.core.client.dto.LeagueDto;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.client.dto.PlayerDto;
import com.jcm.recommendations.soccer.core.config.FootyStatsApiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FootyStatsApiClientTest {

    private WireMockServer wireMock;
    private FootyStatsApiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        FootyStatsApiConfig config = new FootyStatsApiConfig();
        config.setBaseUrl(wireMock.baseUrl());
        config.setKey("test-key");
        client = new FootyStatsApiClient(config, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetchLeaguesSendsKeyAndChosenOnlyAndParsesSeasons() {
        wireMock.stubFor(get(urlPathEqualTo("/league-list")).willReturn(okJson("""
                {"success":true,"data":[{"name":"England Premier League","country":"England",
                  "season":[{"id":12325,"year":"20252026"}]}]}
                """)));

        List<LeagueDto> leagues = client.fetchLeagues();

        assertThat(leagues).singleElement().extracting(LeagueDto::getName).isEqualTo("England Premier League");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/league-list"))
                .withQueryParam("key", equalTo("test-key"))
                .withQueryParam("chosen_leagues_only", equalTo("true")));
    }

    @Test
    void unsuccessfulEnvelopeReturnsEmptyRatherThanThrowing() {
        wireMock.stubFor(get(urlPathEqualTo("/league-matches")).willReturn(okJson("""
                {"success":false,"message":"Invalid season"}
                """)));

        assertThat(client.fetchMatches(1L)).isEmpty();
    }

    @Test
    void serverErrorBecomesApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/league-teams")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchTeams(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("teams for season 1");
    }

    @Test
    void malformedBodyBecomesApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/league-referees")).willReturn(okJson("{not json")));

        assertThatThrownBy(() -> client.fetchReferees(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("parsing referees");
    }

    @Test
    void emptyBodyBecomesApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/league-matches")).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client.fetchMatches(1L)).isInstanceOf(ApiException.class);
    }

    @Test
    void leaguePlayersFollowsPagerToTheLastPage() {
        wireMock.stubFor(get(urlPathEqualTo("/league-players")).withQueryParam("page", equalTo("1")).willReturn(okJson("""
                {"success":true,"pager":{"current_page":1,"max_page":2},"data":[{"id":1,"known_as":"A"}]}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/league-players")).withQueryParam("page", equalTo("2")).willReturn(okJson("""
                {"success":true,"pager":{"current_page":2,"max_page":2},"data":[{"id":2,"known_as":"B"}]}
                """)));

        List<PlayerDto> players = client.fetchLeaguePlayers(99L);

        assertThat(players).extracting(PlayerDto::getId).containsExactly(1L, 2L);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/league-players"))
                .withQueryParam("season_id", equalTo("99"))
                .withQueryParam("include", equalTo("stats")));
    }

    @Test
    void pagingStopsOnAFailedPageAndKeepsWhatItHas() {
        wireMock.stubFor(get(urlPathEqualTo("/todays-matches")).withQueryParam("page", equalTo("1")).willReturn(okJson("""
                {"success":true,"pager":{"current_page":1,"max_page":3},"data":[{"id":10}]}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/todays-matches")).withQueryParam("page", equalTo("2")).willReturn(okJson("""
                {"success":false}
                """)));

        List<MatchDto> matches = client.fetchTodaysMatches("2026-10-10", "Europe/London");

        assertThat(matches).extracting(MatchDto::getId).containsExactly(10L);
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/todays-matches")));
    }

    @Test
    void missingPagerMeansSinglePage() {
        wireMock.stubFor(get(urlPathEqualTo("/todays-matches")).willReturn(okJson("""
                {"success":true,"data":[{"id":10},{"id":11}]}
                """)));

        assertThat(client.fetchTodaysMatches("2026-10-10", "UTC")).hasSize(2);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/todays-matches"))
                .withQueryParam("timezone", equalTo("UTC")));
    }

    @Test
    void fetchMatchAcceptsObjectOrArrayData() {
        wireMock.stubFor(get(urlPathEqualTo("/match")).withQueryParam("match_id", equalTo("1")).willReturn(okJson("""
                {"success":true,"data":{"id":1,"homeID":5}}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/match")).withQueryParam("match_id", equalTo("2")).willReturn(okJson("""
                {"success":true,"data":[{"id":2,"homeID":6}]}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/match")).withQueryParam("match_id", equalTo("3")).willReturn(okJson("""
                {"success":true,"data":[]}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/match")).withQueryParam("match_id", equalTo("4")).willReturn(okJson("""
                {"success":false}
                """)));

        assertThat(client.fetchMatch(1L).getHomeId()).isEqualTo(5L);
        assertThat(client.fetchMatch(2L).getHomeId()).isEqualTo(6L);
        assertThat(client.fetchMatch(3L)).isNull();
        assertThat(client.fetchMatch(4L)).isNull();
    }

    @Test
    void teamFormReturnsFirstEntryOrNull() {
        wireMock.stubFor(get(urlPathEqualTo("/lastx")).withQueryParam("team_id", equalTo("1")).willReturn(okJson("""
                {"success":true,"data":[{"id":1},{"id":1}]}
                """)));
        wireMock.stubFor(get(urlPathEqualTo("/lastx")).withQueryParam("team_id", equalTo("2")).willReturn(okJson("""
                {"success":true,"data":[]}
                """)));

        assertThat(client.fetchTeamForm(1L)).isNotNull();
        assertThat(client.fetchTeamForm(2L)).isNull();
    }
}
