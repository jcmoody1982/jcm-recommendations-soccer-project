package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.repository.*;
import com.jcm.recommendations.soccer.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Component
@RequiredArgsConstructor
@Slf4j
public class FixtureContextBuilder {

    private final FixtureRepository fixtureRepository;
    private final FixtureOddsRepository fixtureOddsRepository;
    private final FixturePotentialsRepository fixturePotentialsRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamSeasonStatsRepository teamSeasonStatsRepository;
    private final TeamRecentFormRepository teamRecentFormRepository;
    private final RefereeStatsRepository refereeStatsRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;

    public FixtureContext buildContext(Fixture fixture) {
        log.debug("Building context for fixture: fixtureId={}", fixture.getId());

        FixtureOdds odds = fixtureOddsRepository.findById(fixture.getId()).orElse(null);
        FixturePotentials potentials = fixturePotentialsRepository.findById(fixture.getId()).orElse(null);
        
        League league = leagueRepository.findById(fixture.getSeasonId()).orElse(null);

        Team homeTeam = teamRepository.findById(fixture.getHomeTeamId()).orElse(null);
        Team awayTeam = teamRepository.findById(fixture.getAwayTeamId()).orElse(null);

        TeamSeasonStats homeStats = null;
        TeamSeasonStats awayStats = null;
        if (homeTeam != null) {
            homeStats = teamSeasonStatsRepository.findByTeamIdAndSeasonId(
                homeTeam.getId(), fixture.getSeasonId()).orElse(null);
        }
        if (awayTeam != null) {
            awayStats = teamSeasonStatsRepository.findByTeamIdAndSeasonId(
                awayTeam.getId(), fixture.getSeasonId()).orElse(null);
        }

        TeamRecentForm homeForm = homeTeam != null 
            ? teamRecentFormRepository.findByTeamId(homeTeam.getId()).orElse(null) : null;
        TeamRecentForm awayForm = awayTeam != null 
            ? teamRecentFormRepository.findByTeamId(awayTeam.getId()).orElse(null) : null;

        RefereeStats refereeStats = null;
        if (fixture.getRefereeId() != null) {
            refereeStats = refereeStatsRepository.findByRefereeIdAndSeasonId(
                fixture.getRefereeId(), fixture.getSeasonId()).orElse(null);
        }

        List<PlayerSeasonStats> homePlayers = emptyList();
        List<PlayerSeasonStats> awayPlayers = emptyList();
        if (fixture.getHomeTeamId() != null && fixture.getAwayTeamId() != null) {
            List<PlayerSeasonStats> players = playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(
                    List.of(fixture.getHomeTeamId(), fixture.getAwayTeamId()),
                    List.of(fixture.getSeasonId()));
            homePlayers = playersForTeam(players, fixture.getHomeTeamId(), fixture.getSeasonId());
            awayPlayers = playersForTeam(players, fixture.getAwayTeamId(), fixture.getSeasonId());
        }

        return FixtureContext.builder()
                .fixture(fixture)
                .odds(odds)
                .potentials(potentials)
                .league(league)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .refereeStats(refereeStats)
                .homePlayers(homePlayers)
                .awayPlayers(awayPlayers)
                .build();
    }

    public List<FixtureContext> buildContextsForFixtures(List<Fixture> fixtures) {
        if (fixtures.isEmpty()) {
            return Collections.emptyList();
        }
        
        log.info("Building contexts for fixtures (batch mode): count={}", fixtures.size());
        long startTime = System.currentTimeMillis();
        
        // Collect all IDs needed for batch queries
        Set<Long> fixtureIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        Set<Long> seasonIds = new HashSet<>();
        Set<Long> refereeIds = new HashSet<>();
        
        for (Fixture fixture : fixtures) {
            fixtureIds.add(fixture.getId());
            seasonIds.add(fixture.getSeasonId());
            if (fixture.getHomeTeamId() != null) teamIds.add(fixture.getHomeTeamId());
            if (fixture.getAwayTeamId() != null) teamIds.add(fixture.getAwayTeamId());
            if (fixture.getRefereeId() != null) refereeIds.add(fixture.getRefereeId());
        }
        
        // Batch fetch all related data
        Map<Long, FixtureOdds> oddsMap = fetchOddsMap(fixtureIds);
        Map<Long, FixturePotentials> potentialsMap = fetchPotentialsMap(fixtureIds);
        Map<Long, League> leagueMap = fetchLeagueMap(seasonIds);
        Map<Long, Team> teamMap = fetchTeamMap(teamIds);
        Map<String, TeamSeasonStats> teamStatsMap = fetchTeamStatsMap(teamIds, seasonIds);
        Map<Long, TeamRecentForm> teamFormMap = fetchTeamFormMap(teamIds);
        Map<String, RefereeStats> refereeStatsMap = fetchRefereeStatsMap(refereeIds, seasonIds);
        Map<String, List<PlayerSeasonStats>> playersMap = fetchPlayersMap(teamIds, seasonIds);
        
        long fetchTime = System.currentTimeMillis() - startTime;
        log.debug("Batch data fetched in {}ms: odds={}, potentials={}, leagues={}, teams={}, teamStats={}, teamForms={}, refereeStats={}",
                fetchTime, oddsMap.size(), potentialsMap.size(), leagueMap.size(), 
                teamMap.size(), teamStatsMap.size(), teamFormMap.size(), refereeStatsMap.size());
        
        // Build contexts using pre-fetched data
        List<FixtureContext> contexts = new ArrayList<>();
        int completeCount = 0;
        
        for (Fixture fixture : fixtures) {
            FixtureContext context = buildContextFromMaps(
                    fixture, oddsMap, potentialsMap, leagueMap, teamMap, 
                    teamStatsMap, teamFormMap, refereeStatsMap, playersMap);
            
            if (context.hasCompleteData()) {
                contexts.add(context);
                completeCount++;
            } else {
                log.debug("Skipping fixture with incomplete data: fixtureId={}", fixture.getId());
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fixture contexts built (batch mode): total={}, complete={}, skipped={}, duration={}ms", 
                fixtures.size(), completeCount, fixtures.size() - completeCount, totalTime);
        
        return contexts;
    }
    
    private FixtureContext buildContextFromMaps(
            Fixture fixture,
            Map<Long, FixtureOdds> oddsMap,
            Map<Long, FixturePotentials> potentialsMap,
            Map<Long, League> leagueMap,
            Map<Long, Team> teamMap,
            Map<String, TeamSeasonStats> teamStatsMap,
            Map<Long, TeamRecentForm> teamFormMap,
            Map<String, RefereeStats> refereeStatsMap,
            Map<String, List<PlayerSeasonStats>> playersMap) {
        
        Long fixtureId = fixture.getId();
        Long seasonId = fixture.getSeasonId();
        Long homeTeamId = fixture.getHomeTeamId();
        Long awayTeamId = fixture.getAwayTeamId();
        Long refereeId = fixture.getRefereeId();
        
        Team homeTeam = homeTeamId != null ? teamMap.get(homeTeamId) : null;
        Team awayTeam = awayTeamId != null ? teamMap.get(awayTeamId) : null;
        
        return FixtureContext.builder()
                .fixture(fixture)
                .odds(oddsMap.get(fixtureId))
                .potentials(potentialsMap.get(fixtureId))
                .league(leagueMap.get(seasonId))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeTeamStats(homeTeamId != null ? teamStatsMap.get(teamStatsKey(homeTeamId, seasonId)) : null)
                .awayTeamStats(awayTeamId != null ? teamStatsMap.get(teamStatsKey(awayTeamId, seasonId)) : null)
                .homeTeamForm(homeTeamId != null ? teamFormMap.get(homeTeamId) : null)
                .awayTeamForm(awayTeamId != null ? teamFormMap.get(awayTeamId) : null)
                .refereeStats(refereeId != null ? refereeStatsMap.get(refereeStatsKey(refereeId, seasonId)) : null)
                .homePlayers(homeTeamId != null
                        ? playersMap.getOrDefault(teamStatsKey(homeTeamId, seasonId), emptyList())
                        : emptyList())
                .awayPlayers(awayTeamId != null
                        ? playersMap.getOrDefault(teamStatsKey(awayTeamId, seasonId), emptyList())
                        : emptyList())
                .build();
    }
    
    private Map<Long, FixtureOdds> fetchOddsMap(Set<Long> fixtureIds) {
        return fixtureOddsRepository.findAllById(fixtureIds).stream()
                .collect(Collectors.toMap(FixtureOdds::getFixtureId, Function.identity()));
    }
    
    private Map<Long, FixturePotentials> fetchPotentialsMap(Set<Long> fixtureIds) {
        return fixturePotentialsRepository.findAllById(fixtureIds).stream()
                .collect(Collectors.toMap(FixturePotentials::getFixtureId, Function.identity()));
    }
    
    private Map<Long, League> fetchLeagueMap(Set<Long> seasonIds) {
        return leagueRepository.findAllById(seasonIds).stream()
                .collect(Collectors.toMap(League::getCurrentSeasonId, Function.identity()));
    }
    
    private Map<Long, Team> fetchTeamMap(Set<Long> teamIds) {
        return teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
    }
    
    private Map<String, TeamSeasonStats> fetchTeamStatsMap(Set<Long> teamIds, Set<Long> seasonIds) {
        if (teamIds.isEmpty() || seasonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return teamSeasonStatsRepository.findByTeamIdsAndSeasonIds(teamIds, seasonIds).stream()
                .collect(Collectors.toMap(
                        stats -> teamStatsKey(stats.getTeamId(), stats.getSeasonId()),
                        Function.identity(),
                        (existing, replacement) -> existing));
    }
    
    private Map<Long, TeamRecentForm> fetchTeamFormMap(Set<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return teamRecentFormRepository.findByTeamIdIn(teamIds).stream()
                .collect(Collectors.toMap(TeamRecentForm::getTeamId, Function.identity()));
    }
    
    private Map<String, RefereeStats> fetchRefereeStatsMap(Set<Long> refereeIds, Set<Long> seasonIds) {
        if (refereeIds.isEmpty() || seasonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return refereeStatsRepository.findByRefereeIdsAndSeasonIds(refereeIds, seasonIds).stream()
                .collect(Collectors.toMap(
                        stats -> refereeStatsKey(stats.getRefereeId(), stats.getSeasonId()),
                        Function.identity(),
                        (existing, replacement) -> existing));
    }
    
    private Map<String, List<PlayerSeasonStats>> fetchPlayersMap(Set<Long> teamIds, Set<Long> seasonIds) {
        if (teamIds.isEmpty() || seasonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<PlayerSeasonStats>> grouped = new HashMap<>();
        for (PlayerSeasonStats stats : playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(teamIds, seasonIds)) {
            addPlayerToGroup(grouped, stats.getClubTeamId(), stats.getSeasonId(), stats);
            addPlayerToGroup(grouped, stats.getClubTeam2Id(), stats.getSeasonId(), stats);
        }
        return grouped;
    }

    private static void addPlayerToGroup(
            Map<String, List<PlayerSeasonStats>> grouped,
            Long teamId,
            Long seasonId,
            PlayerSeasonStats stats) {
        if (teamId == null || seasonId == null) {
            return;
        }
        grouped.computeIfAbsent(teamId + ":" + seasonId, key -> new ArrayList<>()).add(stats);
    }

    private static List<PlayerSeasonStats> playersForTeam(
            List<PlayerSeasonStats> players, Long teamId, Long seasonId) {
        if (players == null || players.isEmpty() || teamId == null) {
            return emptyList();
        }
        return players.stream()
                .filter(player -> belongsToTeam(player, teamId, seasonId))
                .toList();
    }

    private static boolean belongsToTeam(PlayerSeasonStats player, Long teamId, Long seasonId) {
        if (player == null || teamId == null) {
            return false;
        }
        if (seasonId != null && player.getSeasonId() != null && !seasonId.equals(player.getSeasonId())) {
            return false;
        }
        return teamId.equals(player.getClubTeamId()) || teamId.equals(player.getClubTeam2Id());
    }

    private String teamStatsKey(Long teamId, Long seasonId) {
        return teamId + ":" + seasonId;
    }
    
    private String refereeStatsKey(Long refereeId, Long seasonId) {
        return refereeId + ":" + seasonId;
    }
}
