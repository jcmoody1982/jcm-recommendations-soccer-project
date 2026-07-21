package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.LeagueDto;
import com.jcm.recommendations.soccer.domain.League;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeagueMapper {

    public League toEntity(LeagueDto dto) {
        if (dto == null || dto.getSeason() == null || dto.getSeason().isEmpty()) {
            return null;
        }

        LeagueDto.SeasonDto currentSeason = dto.getSeason().get(dto.getSeason().size() - 1);

        return League.builder()
                .currentSeasonId(currentSeason.getId())
                .name(dto.getName())
                .country(dto.getCountry())
                .image(dto.getImage())
                .build();
    }

    public List<League> toEntities(List<LeagueDto> dtos) {
        return dtos.stream()
                .map(this::toEntity)
                .filter(league -> league != null)
                .toList();
    }
}
