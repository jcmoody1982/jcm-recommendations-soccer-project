package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.repository.TeamSquadProfileRepository;
import com.jcm.recommendations.soccer.core.transfermarkt.client.TransfermarktClient;
import com.jcm.recommendations.soccer.core.transfermarkt.config.TransfermarktProperties;
import com.jcm.recommendations.soccer.core.transfermarkt.dto.ClubSquadDto;
import com.jcm.recommendations.soccer.core.transfermarkt.mapper.TransfermarktMapper;
import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import com.jcm.recommendations.soccer.domain.TeamTransfermarktMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamSquadProfileService {

    private final TransfermarktClient transfermarktClient;
    private final TransfermarktProperties properties;
    private final TeamSquadProfileRepository squadProfileRepository;
    private final TeamTransfermarktMappingService mappingService;
    private final TransfermarktMapper transfermarktMapper;

    @Transactional
    public SyncResult syncSquadProfilesForTeams(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty() || !transfermarktClient.isEnabled()) {
            return new SyncResult(0, 0, 0, 0);
        }

        int synced = 0;
        int skippedFresh = 0;
        int skippedUnmapped = 0;
        int failed = 0;
        String season = resolveSeason();

        for (Long teamId : teamIds) {
            if (teamId == null) {
                continue;
            }

            try {
                Optional<TeamTransfermarktMapping> mappingOpt = mappingService.findMapping(teamId);
                if (mappingOpt.isEmpty()) {
                    skippedUnmapped++;
                    continue;
                }

                TeamTransfermarktMapping mapping = mappingOpt.get();
                if (mapping.getTransfermarktClubId() == null) {
                    skippedUnmapped++;
                    continue;
                }

                Optional<TeamSquadProfile> existing = squadProfileRepository.findById(teamId);
                if (existing.isPresent() && isFresh(existing.get().getFetchedAt())) {
                    skippedFresh++;
                    continue;
                }

                Optional<ClubSquadDto> squadOpt =
                        transfermarktClient.fetchClubSquad(mapping.getTransfermarktClubId(), season);
                if (squadOpt.isEmpty()) {
                    failed++;
                    sleepQuietly();
                    continue;
                }

                boolean engineUsable = mappingService.isEngineUsable(mapping);
                TeamSquadProfile profile = transfermarktMapper.toTeamSquadProfile(
                        teamId, squadOpt.get(), engineUsable, Instant.now());
                if (profile == null) {
                    failed++;
                } else {
                    squadProfileRepository.save(profile);
                    synced++;
                }

                sleepQuietly();
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync squad profile: teamId={}, error={}", teamId, e.getMessage());
            }
        }

        log.info("Squad profile sync complete: synced={}, skippedFresh={}, skippedUnmapped={}, failed={}",
                synced, skippedFresh, skippedUnmapped, failed);
        return new SyncResult(synced, skippedFresh, skippedUnmapped, failed);
    }

    boolean isFresh(Instant fetchedAt) {
        if (fetchedAt == null) {
            return false;
        }
        LocalDate fetchedDay = fetchedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(1, properties.getFreshnessDays()));
        return !fetchedDay.isBefore(cutoff);
    }

    private String resolveSeason() {
        if (StringUtils.hasText(properties.getSeason())) {
            return properties.getSeason().trim();
        }
        return String.valueOf(LocalDate.now(ZoneOffset.UTC).getYear());
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(Math.max(0L, properties.getRateLimitMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SyncResult(int synced, int skippedFresh, int skippedUnmapped, int failed) {}
}
