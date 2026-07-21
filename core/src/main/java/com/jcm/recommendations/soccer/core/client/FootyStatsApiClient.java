package com.jcm.recommendations.soccer.core.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.client.dto.ApiResponse;
import com.jcm.recommendations.soccer.core.client.dto.LeagueDto;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.client.dto.RefereeDto;
import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.core.config.FootyStatsApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class FootyStatsApiClient {

    private final FootyStatsApiConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FootyStatsApiClient(FootyStatsApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
    }

    public List<LeagueDto> fetchLeagues() {
        log.info("Fetching league list from API");
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/league-list")
                            .queryParam("key", config.getKey())
                            .queryParam("chosen_leagues_only", "true")
                            .build())
                    .retrieve()
                    .body(String.class);

            ApiResponse<LeagueDto> apiResponse = objectMapper.readValue(
                    response, new TypeReference<ApiResponse<LeagueDto>>() {});

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null) {
                log.info("League list fetched successfully: count={}", apiResponse.getData().size());
                return apiResponse.getData();
            }

            log.warn("API returned unsuccessful response for league list");
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Failed to fetch league list: error={}", e.getMessage(), e);
            throw new ApiException("Failed to fetch league list", e);
        } catch (Exception e) {
            log.error("Error parsing league list response: error={}", e.getMessage(), e);
            throw new ApiException("Error parsing league list response", e);
        }
    }

    public List<MatchDto> fetchMatches(Long seasonId) {
        log.info("Fetching matches for season: seasonId={}", seasonId);
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/league-matches")
                            .queryParam("key", config.getKey())
                            .queryParam("season_id", seasonId)
                            .build())
                    .retrieve()
                    .body(String.class);

            ApiResponse<MatchDto> apiResponse = objectMapper.readValue(
                    response, new TypeReference<ApiResponse<MatchDto>>() {});

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null) {
                log.info("Matches fetched: seasonId={}, count={}", seasonId, apiResponse.getData().size());
                return apiResponse.getData();
            }

            log.warn("API returned unsuccessful response for matches: seasonId={}", seasonId);
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Failed to fetch matches: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Failed to fetch matches for season " + seasonId, e);
        } catch (Exception e) {
            log.error("Error parsing matches response: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Error parsing matches response", e);
        }
    }

    public List<TeamDto> fetchTeams(Long seasonId) {
        log.info("Fetching teams for season: seasonId={}", seasonId);
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/league-teams")
                            .queryParam("key", config.getKey())
                            .queryParam("season_id", seasonId)
                            .queryParam("include", "stats")
                            .build())
                    .retrieve()
                    .body(String.class);

            ApiResponse<TeamDto> apiResponse = objectMapper.readValue(
                    response, new TypeReference<ApiResponse<TeamDto>>() {});

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null) {
                log.info("Teams fetched: seasonId={}, count={}", seasonId, apiResponse.getData().size());
                return apiResponse.getData();
            }

            log.warn("API returned unsuccessful response for teams: seasonId={}", seasonId);
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Failed to fetch teams: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Failed to fetch teams for season " + seasonId, e);
        } catch (Exception e) {
            log.error("Error parsing teams response: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Error parsing teams response", e);
        }
    }

    public List<RefereeDto> fetchReferees(Long seasonId) {
        log.info("Fetching referees for season: seasonId={}", seasonId);
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/league-referees")
                            .queryParam("key", config.getKey())
                            .queryParam("season_id", seasonId)
                            .build())
                    .retrieve()
                    .body(String.class);

            ApiResponse<RefereeDto> apiResponse = objectMapper.readValue(
                    response, new TypeReference<ApiResponse<RefereeDto>>() {});

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null) {
                log.info("Referees fetched: seasonId={}, count={}", seasonId, apiResponse.getData().size());
                return apiResponse.getData();
            }

            log.warn("API returned unsuccessful response for referees: seasonId={}", seasonId);
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Failed to fetch referees: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Failed to fetch referees for season " + seasonId, e);
        } catch (Exception e) {
            log.error("Error parsing referees response: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw new ApiException("Error parsing referees response", e);
        }
    }

    public TeamDto fetchTeamForm(Long teamId) {
        log.info("Fetching recent form for team: teamId={}", teamId);
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/lastx")
                            .queryParam("key", config.getKey())
                            .queryParam("team_id", teamId)
                            .build())
                    .retrieve()
                    .body(String.class);

            ApiResponse<TeamDto> apiResponse = objectMapper.readValue(
                    response, new TypeReference<ApiResponse<TeamDto>>() {});

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null 
                    && !apiResponse.getData().isEmpty()) {
                log.info("Team form fetched: teamId={}", teamId);
                return apiResponse.getData().get(0);
            }

            log.warn("API returned unsuccessful response for team form: teamId={}", teamId);
            return null;

        } catch (RestClientException e) {
            log.error("Failed to fetch team form: teamId={}, error={}", teamId, e.getMessage(), e);
            throw new ApiException("Failed to fetch team form for team " + teamId, e);
        } catch (Exception e) {
            log.error("Error parsing team form response: teamId={}, error={}", teamId, e.getMessage(), e);
            throw new ApiException("Error parsing team form response", e);
        }
    }
}
