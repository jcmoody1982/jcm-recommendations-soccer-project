package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.RefereeDto;
import com.jcm.recommendations.soccer.core.mapper.RefereeMapper;
import com.jcm.recommendations.soccer.core.repository.RefereeRepository;
import com.jcm.recommendations.soccer.core.repository.RefereeStatsRepository;
import com.jcm.recommendations.soccer.domain.Referee;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefereeService {

    private final FootyStatsApiClient apiClient;
    private final RefereeRepository refereeRepository;
    private final RefereeStatsRepository refereeStatsRepository;
    private final RefereeMapper refereeMapper;

    @Transactional
    public SyncResult syncRefereesForSeason(Long seasonId, String leagueName) {
        log.info("Fetching referees for season: seasonId={}, league={}", seasonId, leagueName);

        List<RefereeDto> refereeDtos = apiClient.fetchReferees(seasonId);
        log.info("Referees fetched: seasonId={}, count={}", seasonId, refereeDtos.size());

        int newCount = 0;
        int updatedCount = 0;

        for (RefereeDto refereeDto : refereeDtos) {
            boolean refereeExists = refereeRepository.existsById(refereeDto.getId());

            Referee referee = refereeMapper.toReferee(refereeDto, seasonId);
            refereeRepository.save(referee);

            RefereeStats stats = refereeMapper.toRefereeStats(refereeDto, seasonId);
            Optional<RefereeStats> existingStats = 
                    refereeStatsRepository.findByRefereeIdAndSeasonId(refereeDto.getId(), seasonId);

            if (existingStats.isPresent()) {
                stats.setId(existingStats.get().getId());
                updatedCount++;
            } else {
                newCount++;
            }
            refereeStatsRepository.save(stats);
        }

        log.info("Referees persisted for season {}: new={}, updated={}", seasonId, newCount, updatedCount);
        return new SyncResult(newCount, updatedCount, 0);
    }

    public List<Referee> getAllReferees() {
        return refereeRepository.findAll();
    }

    public List<Referee> getRefereesBySeasonId(Long seasonId) {
        return refereeRepository.findBySeasonId(seasonId);
    }

    public Referee getRefereeById(Long refereeId) {
        return refereeRepository.findById(refereeId).orElse(null);
    }

    public RefereeStats getRefereeStats(Long refereeId, Long seasonId) {
        return refereeStatsRepository.findByRefereeIdAndSeasonId(refereeId, seasonId).orElse(null);
    }

    public record SyncResult(int newCount, int updatedCount, int unchangedCount) {}
}
