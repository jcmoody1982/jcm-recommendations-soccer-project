package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.mapper.FixtureMapper;
import com.jcm.recommendations.soccer.core.repository.FixtureOddsRepository;
import com.jcm.recommendations.soccer.core.repository.FixturePotentialsRepository;
import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixtureService {

    private static final String STATUS_INCOMPLETE = "incomplete";
    private static final int DAYS_AHEAD = 7;
    private static final long SECONDS_PER_DAY = 86400L;

    private final FootyStatsApiClient apiClient;
    private final FixtureRepository fixtureRepository;
    private final FixtureOddsRepository fixtureOddsRepository;
    private final FixturePotentialsRepository fixturePotentialsRepository;
    private final FixtureMapper fixtureMapper;

    @Transactional
    public SyncResult syncFixturesForSeason(Long seasonId, String leagueName) {
        log.info("Fetching fixtures for season: seasonId={}, league={}", seasonId, leagueName);

        List<MatchDto> matchDtos = apiClient.fetchMatches(seasonId);

        long now = Instant.now().getEpochSecond();
        long endTime = now + (DAYS_AHEAD * SECONDS_PER_DAY);

        List<MatchDto> upcomingMatches = matchDtos.stream()
                .filter(m -> m.getDateUnix() != null)
                .filter(m -> m.getDateUnix() >= now && m.getDateUnix() <= endTime)
                .toList();

        log.info("Fixtures fetched: seasonId={}, total={}, upcoming7Days={}", 
                seasonId, matchDtos.size(), upcomingMatches.size());

        int newCount = 0;
        int updatedCount = 0;

        for (MatchDto matchDto : upcomingMatches) {
            boolean exists = fixtureRepository.existsById(matchDto.getId());

            Fixture fixture = fixtureMapper.toFixture(matchDto, seasonId);
            fixtureRepository.save(fixture);

            FixtureOdds odds = fixtureMapper.toFixtureOdds(matchDto);
            fixtureOddsRepository.save(odds);

            FixturePotentials potentials = fixtureMapper.toFixturePotentials(matchDto);
            fixturePotentialsRepository.save(potentials);

            if (exists) {
                updatedCount++;
            } else {
                newCount++;
            }
        }

        log.info("Fixtures persisted for season {}: new={}, updated={}", seasonId, newCount, updatedCount);
        return new SyncResult(newCount, updatedCount, 0);
    }

    public List<Fixture> getUpcomingFixtures() {
        long now = Instant.now().getEpochSecond();
        long endTime = now + (DAYS_AHEAD * SECONDS_PER_DAY);
        return fixtureRepository.findByDateRangeAndStatus(now, endTime, STATUS_INCOMPLETE);
    }

    public List<Fixture> getUpcomingFixtures(int daysAhead) {
        long now = Instant.now().getEpochSecond();
        long endTime = now + (daysAhead * SECONDS_PER_DAY);
        return fixtureRepository.findByDateRangeAndStatus(now, endTime, STATUS_INCOMPLETE);
    }

    public List<Fixture> getFixturesBySeasonId(Long seasonId) {
        return fixtureRepository.findBySeasonId(seasonId);
    }

    public Fixture getFixtureById(Long fixtureId) {
        return fixtureRepository.findById(fixtureId).orElse(null);
    }

    public FixtureOdds getFixtureOdds(Long fixtureId) {
        return fixtureOddsRepository.findById(fixtureId).orElse(null);
    }

    public FixturePotentials getFixturePotentials(Long fixtureId) {
        return fixturePotentialsRepository.findById(fixtureId).orElse(null);
    }

    public record SyncResult(int newCount, int updatedCount, int unchangedCount) {}
}
