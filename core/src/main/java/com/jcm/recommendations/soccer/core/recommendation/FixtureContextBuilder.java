package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.repository.*;
import com.jcm.recommendations.soccer.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
                .build();
    }

    public List<FixtureContext> buildContextsForFixtures(List<Fixture> fixtures) {
        log.info("Building contexts for fixtures: count={}", fixtures.size());
        
        List<FixtureContext> contexts = new ArrayList<>();
        int completeCount = 0;
        
        for (Fixture fixture : fixtures) {
            FixtureContext context = buildContext(fixture);
            if (context.hasCompleteData()) {
                contexts.add(context);
                completeCount++;
            } else {
                log.debug("Skipping fixture with incomplete data: fixtureId={}", fixture.getId());
            }
        }
        
        log.info("Fixture contexts built: total={}, complete={}, skipped={}", 
                fixtures.size(), completeCount, fixtures.size() - completeCount);
        
        return contexts;
    }
}
