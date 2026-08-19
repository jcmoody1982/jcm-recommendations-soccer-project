package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.PlayerDto;
import com.jcm.recommendations.soccer.core.mapper.PlayerMapper;
import com.jcm.recommendations.soccer.core.repository.PlayerRepository;
import com.jcm.recommendations.soccer.core.repository.PlayerSeasonStatsRepository;
import com.jcm.recommendations.soccer.domain.Player;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final FootyStatsApiClient apiClient;
    private final PlayerRepository playerRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;
    private final PlayerMapper playerMapper;

    @Transactional
    public SyncResult syncPlayersForSeason(Long seasonId, String leagueName) {
        log.info("Fetching players for season: seasonId={}, league={}", seasonId, leagueName);

        List<PlayerDto> playerDtos = apiClient.fetchLeaguePlayers(seasonId);
        log.info("Players fetched: seasonId={}, count={}", seasonId, playerDtos.size());

        int newCount = 0;
        int updatedCount = 0;
        int skipped = 0;

        for (PlayerDto dto : playerDtos) {
            if (dto == null || dto.getId() == null) {
                skipped++;
                continue;
            }
            if (dto.getClubTeamId() == null || dto.getClubTeamId() <= 0) {
                skipped++;
                continue;
            }

            Player player = playerMapper.toPlayer(dto, seasonId);
            playerRepository.save(player);

            PlayerSeasonStats stats = playerMapper.toSeasonStats(dto, seasonId);
            Optional<PlayerSeasonStats> existing =
                    playerSeasonStatsRepository.findByPlayerIdAndSeasonId(dto.getId(), seasonId);
            if (existing.isPresent()) {
                stats.setId(existing.get().getId());
                updatedCount++;
            } else {
                newCount++;
            }
            playerSeasonStatsRepository.save(stats);
        }

        log.info("Players persisted for season {}: new={}, updated={}, skipped={}",
                seasonId, newCount, updatedCount, skipped);
        return new SyncResult(newCount, updatedCount, skipped);
    }

    public record SyncResult(int newCount, int updatedCount, int skippedCount) {}
}
