package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.core.mapper.TeamMapper;
import com.jcm.recommendations.soccer.core.repository.TeamRepository;
import com.jcm.recommendations.soccer.core.repository.TeamSeasonStatsRepository;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final FootyStatsApiClient apiClient;
    private final TeamRepository teamRepository;
    private final TeamSeasonStatsRepository teamSeasonStatsRepository;
    private final TeamMapper teamMapper;

    @Transactional
    public SyncResult syncTeamsForSeason(Long seasonId, String leagueName) {
        log.info("Fetching teams for season: seasonId={}, league={}", seasonId, leagueName);

        List<TeamDto> teamDtos = apiClient.fetchTeams(seasonId);
        log.info("Teams fetched: seasonId={}, count={}", seasonId, teamDtos.size());

        int newCount = 0;
        int updatedCount = 0;

        for (TeamDto teamDto : teamDtos) {
            boolean teamExists = teamRepository.existsById(teamDto.getId());

            Team team = teamMapper.toTeam(teamDto, seasonId);
            teamRepository.save(team);

            TeamSeasonStats stats = teamMapper.toTeamSeasonStats(teamDto, seasonId);
            Optional<TeamSeasonStats> existingStats = 
                    teamSeasonStatsRepository.findByTeamIdAndSeasonId(teamDto.getId(), seasonId);

            if (existingStats.isPresent()) {
                stats.setId(existingStats.get().getId());
                updatedCount++;
            } else {
                newCount++;
            }
            teamSeasonStatsRepository.save(stats);
        }

        log.info("Teams persisted for season {}: new={}, updated={}", seasonId, newCount, updatedCount);
        return new SyncResult(newCount, updatedCount, 0);
    }

    public List<Team> getAllTeams() {
        return teamRepository.findAll();
    }

    public List<Team> getTeamsBySeasonId(Long seasonId) {
        return teamRepository.findBySeasonId(seasonId);
    }

    public Team getTeamById(Long teamId) {
        return teamRepository.findById(teamId).orElse(null);
    }

    public TeamSeasonStats getTeamSeasonStats(Long teamId, Long seasonId) {
        return teamSeasonStatsRepository.findByTeamIdAndSeasonId(teamId, seasonId).orElse(null);
    }

    public record SyncResult(int newCount, int updatedCount, int unchangedCount) {}
}
