package com.jcm.recommendations.soccer.core.transfermarkt.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.transfermarkt.config.TransfermarktProperties;
import com.jcm.recommendations.soccer.core.transfermarkt.dto.ClubSquadDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Component
@Slf4j
public class TransfermarktClient {

    private final TransfermarktProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TransfermarktClient(TransfermarktProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getBaseUrl());
    }

    public Optional<ClubSquadDto> fetchClubSquad(long transfermarktClubId, String season) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                    .path("/clubs/{clubId}/squad");

            if (StringUtils.hasText(season)) {
                builder.queryParam("season", season);
            }
            if (StringUtils.hasText(properties.getApiKey())) {
                builder.queryParam("key", properties.getApiKey());
            }

            String url = builder.buildAndExpand(transfermarktClubId).toUriString();
            log.debug("Fetching Transfermarkt squad: clubId={}, season={}", transfermarktClubId, season);

            String response = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(response)) {
                return Optional.empty();
            }

            ClubSquadDto dto = objectMapper.readValue(response, ClubSquadDto.class);
            if (dto.getClubId() == null) {
                dto.setClubId(transfermarktClubId);
            }
            if (!StringUtils.hasText(dto.getSeason())) {
                dto.setSeason(season);
            }
            return Optional.of(dto);

        } catch (RestClientException e) {
            log.warn("Failed to fetch Transfermarkt squad: clubId={}, error={}", transfermarktClubId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error parsing Transfermarkt squad: clubId={}, error={}", transfermarktClubId, e.getMessage());
            return Optional.empty();
        }
    }
}
