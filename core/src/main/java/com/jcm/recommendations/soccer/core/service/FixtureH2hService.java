package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.mapper.FixtureMapper;
import com.jcm.recommendations.soccer.core.repository.FixtureHeadToHeadRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureHeadToHead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixtureH2hService {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final long RATE_LIMIT_SLEEP_MS = 200L;

    private final FootyStatsApiClient apiClient;
    private final FixtureHeadToHeadRepository headToHeadRepository;
    private final FixtureMapper fixtureMapper;

    @Transactional
    public SyncResult syncHeadToHeadForUpcoming(List<Fixture> upcomingFixtures) {
        if (upcomingFixtures == null || upcomingFixtures.isEmpty()) {
            return new SyncResult(0, 0, 0);
        }

        int synced = 0;
        int skippedFresh = 0;
        int failed = 0;
        LocalDate todayLondon = LocalDate.now(LONDON);

        for (Fixture fixture : upcomingFixtures) {
            if (fixture == null || fixture.getId() == null) {
                continue;
            }
            if (fixture.getStatus() != null && !"incomplete".equalsIgnoreCase(fixture.getStatus())) {
                continue;
            }

            try {
                Optional<FixtureHeadToHead> existing = headToHeadRepository.findById(fixture.getId());
                if (existing.isPresent() && isFreshForLondonDay(existing.get().getFetchedAt(), todayLondon)) {
                    skippedFresh++;
                    continue;
                }

                MatchDto matchDto = apiClient.fetchMatch(fixture.getId());
                FixtureHeadToHead h2h = fixtureMapper.toFixtureHeadToHead(matchDto, Instant.now());
                if (h2h == null) {
                    log.debug("No H2H payload for fixtureId={}", fixture.getId());
                    failed++;
                } else {
                    headToHeadRepository.save(h2h);
                    synced++;
                }

                sleepQuietly();
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync H2H for fixtureId={}: {}", fixture.getId(), e.getMessage());
            }
        }

        log.info("H2H sync complete: synced={}, skippedFresh={}, failed={}", synced, skippedFresh, failed);
        return new SyncResult(synced, skippedFresh, failed);
    }

    boolean isFreshForLondonDay(Instant fetchedAt, LocalDate todayLondon) {
        if (fetchedAt == null) {
            return false;
        }
        LocalDate fetchedDay = fetchedAt.atZone(LONDON).toLocalDate();
        return fetchedDay.equals(todayLondon);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(RATE_LIMIT_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SyncResult(int synced, int skippedFresh, int failed) {}
}
