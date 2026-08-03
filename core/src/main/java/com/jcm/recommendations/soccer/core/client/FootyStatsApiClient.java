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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class FootyStatsApiClient {

    private final FootyStatsApiConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FootyStatsApiClient(FootyStatsApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public List<LeagueDto> fetchLeagues() {
        log.info("Fetching league list from API");
        try {
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/league-list")
                    .queryParam("key", config.getKey())
                    .queryParam("chosen_leagues_only", "true")
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);

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
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/league-matches")
                    .queryParam("key", config.getKey())
                    .queryParam("season_id", seasonId)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);

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
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/league-teams")
                    .queryParam("key", config.getKey())
                    .queryParam("season_id", seasonId)
                    .queryParam("include", "stats")
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);

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
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/league-referees")
                    .queryParam("key", config.getKey())
                    .queryParam("season_id", seasonId)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);

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

    public List<MatchDto> fetchTodaysMatches(String date, String timezone) {
        log.info("Fetching todays-matches: date={}, timezone={}", date, timezone);
        List<MatchDto> all = new ArrayList<>();
        int page = 1;
        int maxPage = 1;
        try {
            do {
                String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                        .path("/todays-matches")
                        .queryParam("key", config.getKey())
                        .queryParam("date", date)
                        .queryParam("timezone", timezone)
                        .queryParam("page", page)
                        .toUriString();

                String response = restTemplate.getForObject(url, String.class);
                ApiResponse<MatchDto> apiResponse = objectMapper.readValue(
                        response, new TypeReference<ApiResponse<MatchDto>>() {});

                if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
                    log.warn("API returned unsuccessful response for todays-matches: date={}, page={}", date, page);
                    break;
                }

                all.addAll(apiResponse.getData());
                if (apiResponse.getPager() != null && apiResponse.getPager().getMaxPage() > 0) {
                    maxPage = apiResponse.getPager().getMaxPage();
                } else {
                    maxPage = page;
                }
                page++;
            } while (page <= maxPage);

            log.info("Todays-matches fetched: date={}, count={}, pages={}", date, all.size(), maxPage);
            return all;
        } catch (RestClientException e) {
            log.error("Failed to fetch todays-matches: date={}, error={}", date, e.getMessage(), e);
            throw new ApiException("Failed to fetch todays-matches for " + date, e);
        } catch (Exception e) {
            log.error("Error parsing todays-matches: date={}, error={}", date, e.getMessage(), e);
            throw new ApiException("Error parsing todays-matches for " + date, e);
        }
    }

    public MatchDto fetchMatch(Long matchId) {
        log.info("Fetching match detail: matchId={}", matchId);
        try {
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/match")
                    .queryParam("key", config.getKey())
                    .queryParam("match_id", matchId)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.path("success").asBoolean(false)) {
                log.warn("API returned unsuccessful response for match: matchId={}", matchId);
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                return null;
            }
            if (data.isArray()) {
                if (data.isEmpty()) {
                    return null;
                }
                return objectMapper.convertValue(data.get(0), MatchDto.class);
            }
            return objectMapper.convertValue(data, MatchDto.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch match: matchId={}, error={}", matchId, e.getMessage(), e);
            throw new ApiException("Failed to fetch match " + matchId, e);
        } catch (Exception e) {
            log.error("Error parsing match: matchId={}, error={}", matchId, e.getMessage(), e);
            throw new ApiException("Error parsing match " + matchId, e);
        }
    }

    public TeamDto fetchTeamForm(Long teamId) {
        log.info("Fetching recent form for team: teamId={}", teamId);
        try {
            String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                    .path("/lastx")
                    .queryParam("key", config.getKey())
                    .queryParam("team_id", teamId)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);

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
