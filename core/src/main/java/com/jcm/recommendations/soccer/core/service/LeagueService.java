package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.LeagueDto;
import com.jcm.recommendations.soccer.core.mapper.LeagueMapper;
import com.jcm.recommendations.soccer.core.repository.LeagueRepository;
import com.jcm.recommendations.soccer.domain.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueService {

    private final FootyStatsApiClient apiClient;
    private final LeagueRepository leagueRepository;
    private final LeagueMapper leagueMapper;

    @Transactional
    public SyncResult syncLeagues() {
        log.info("Starting league sync");

        List<LeagueDto> leagueDtos = apiClient.fetchLeagues();
        List<League> leagues = leagueMapper.toEntities(leagueDtos);

        int newCount = 0;
        int updatedCount = 0;

        for (League league : leagues) {
            boolean exists = leagueRepository.existsById(league.getCurrentSeasonId());
            leagueRepository.save(league);

            if (exists) {
                updatedCount++;
            } else {
                newCount++;
            }
        }

        log.info("League sync completed: new={}, updated={}", newCount, updatedCount);
        return new SyncResult(newCount, updatedCount, leagues.size() - newCount - updatedCount);
    }

    public List<League> getAllLeagues() {
        return leagueRepository.findAll();
    }

    public List<Long> getAllSeasonIds() {
        return leagueRepository.findAll().stream()
                .map(League::getCurrentSeasonId)
                .toList();
    }

    public record SyncResult(int newCount, int updatedCount, int unchangedCount) {}
}
