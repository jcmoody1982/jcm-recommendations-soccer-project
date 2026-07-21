package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncService {

    private final LeagueService leagueService;
    private final FixtureService fixtureService;
    private final TeamService teamService;
    private final RefereeService refereeService;
    private final TeamFormService teamFormService;

    public SyncSummary runFullSync() {
        log.info("Starting daily data sync job");
        Instant startTime = Instant.now();

        int leagueCount = 0;
        int fixtureCount = 0;
        int teamCount = 0;
        int refereeCount = 0;
        int formCount = 0;
        int failedSeasons = 0;

        try {
            LeagueService.SyncResult leagueResult = leagueService.syncLeagues();
            leagueCount = leagueResult.newCount() + leagueResult.updatedCount();
            log.info("Leagues synced: new={}, updated={}", leagueResult.newCount(), leagueResult.updatedCount());

            List<League> leagues = leagueService.getAllLeagues();
            int totalLeagues = leagues.size();
            int processedLeagues = 0;

            Set<Long> upcomingTeamIds = new HashSet<>();

            for (League league : leagues) {
                Long seasonId = league.getCurrentSeasonId();
                String leagueName = league.getName();

                try {
                    FixtureService.SyncResult fixtureResult = 
                            fixtureService.syncFixturesForSeason(seasonId, leagueName);
                    fixtureCount += fixtureResult.newCount() + fixtureResult.updatedCount();

                    TeamService.SyncResult teamResult = 
                            teamService.syncTeamsForSeason(seasonId, leagueName);
                    teamCount += teamResult.newCount() + teamResult.updatedCount();

                    RefereeService.SyncResult refereeResult = 
                            refereeService.syncRefereesForSeason(seasonId, leagueName);
                    refereeCount += refereeResult.newCount() + refereeResult.updatedCount();

                    List<Fixture> upcomingFixtures = fixtureService.getUpcomingFixtures();
                    for (Fixture fixture : upcomingFixtures) {
                        if (fixture.getHomeTeamId() != null) {
                            upcomingTeamIds.add(fixture.getHomeTeamId());
                        }
                        if (fixture.getAwayTeamId() != null) {
                            upcomingTeamIds.add(fixture.getAwayTeamId());
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to sync season: seasonId={}, league={}, error={}", 
                            seasonId, leagueName, e.getMessage());
                    failedSeasons++;
                }

                processedLeagues++;
                if (processedLeagues % 10 == 0) {
                    log.info("Progress: {}/{} leagues processed ({}%)", 
                            processedLeagues, totalLeagues, 
                            (processedLeagues * 100) / totalLeagues);
                }
            }

            for (Long teamId : upcomingTeamIds) {
                try {
                    teamFormService.syncTeamForm(teamId);
                    formCount++;
                } catch (Exception e) {
                    log.warn("Failed to sync team form: teamId={}, error={}", teamId, e.getMessage());
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());

            if (failedSeasons > 0) {
                log.warn("Sync completed with errors: successful={}, failed={}", 
                        totalLeagues - failedSeasons, failedSeasons);
            } else {
                log.info("Daily data sync completed successfully");
            }

            log.info("Sync summary: duration={}s, leagues={}, fixtures={}, teams={}, referees={}, forms={}", 
                    duration.getSeconds(), leagueCount, fixtureCount, teamCount, refereeCount, formCount);

            return new SyncSummary(
                    duration.getSeconds(),
                    leagueCount,
                    fixtureCount,
                    teamCount,
                    refereeCount,
                    formCount,
                    failedSeasons == 0
            );

        } catch (Exception e) {
            log.error("Sync job failed: error={}", e.getMessage(), e);
            throw e;
        }
    }

    public record SyncSummary(
            long durationSeconds,
            int leaguesProcessed,
            int fixturesProcessed,
            int teamsProcessed,
            int refereesProcessed,
            int formsProcessed,
            boolean success
    ) {}
}
