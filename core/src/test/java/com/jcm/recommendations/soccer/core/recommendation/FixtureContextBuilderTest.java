package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.repository.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixtureContextBuilderTest {

    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private FixtureOddsRepository fixtureOddsRepository;
    @Mock
    private FixturePotentialsRepository fixturePotentialsRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamSeasonStatsRepository teamSeasonStatsRepository;
    @Mock
    private TeamRecentFormRepository teamRecentFormRepository;
    @Mock
    private RefereeStatsRepository refereeStatsRepository;
    @Mock
    private PlayerSeasonStatsRepository playerSeasonStatsRepository;

    private FixtureContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = new FixtureContextBuilder(
                fixtureRepository,
                fixtureOddsRepository,
                fixturePotentialsRepository,
                leagueRepository,
                teamRepository,
                teamSeasonStatsRepository,
                teamRecentFormRepository,
                refereeStatsRepository,
                playerSeasonStatsRepository
        );
    }

    @Test
    void buildContextsForFixtures_withEmptyList_returnsEmptyList() {
        List<FixtureContext> result = contextBuilder.buildContextsForFixtures(Collections.emptyList());
        
        assertThat(result).isEmpty();
        verifyNoInteractions(fixtureOddsRepository, fixturePotentialsRepository, leagueRepository,
                teamRepository, teamSeasonStatsRepository, teamRecentFormRepository, refereeStatsRepository,
                playerSeasonStatsRepository);
    }

    @Test
    void buildContextsForFixtures_usesBatchQueries() {
        Fixture fixture1 = createFixture(1L, 100L, 10L, 11L, 50L);
        Fixture fixture2 = createFixture(2L, 100L, 12L, 13L, 51L);
        List<Fixture> fixtures = List.of(fixture1, fixture2);

        Team homeTeam1 = createTeam(10L, "Home1");
        Team awayTeam1 = createTeam(11L, "Away1");
        Team homeTeam2 = createTeam(12L, "Home2");
        Team awayTeam2 = createTeam(13L, "Away2");

        TeamSeasonStats stats10 = createTeamStats(10L, 100L);
        TeamSeasonStats stats11 = createTeamStats(11L, 100L);
        TeamSeasonStats stats12 = createTeamStats(12L, 100L);
        TeamSeasonStats stats13 = createTeamStats(13L, 100L);

        when(fixtureOddsRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(fixturePotentialsRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(leagueRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(teamRepository.findAllById(anyCollection()))
                .thenReturn(List.of(homeTeam1, awayTeam1, homeTeam2, awayTeam2));
        when(teamSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(List.of(stats10, stats11, stats12, stats13));
        when(teamRecentFormRepository.findByTeamIdIn(anyCollection())).thenReturn(Collections.emptyList());
        when(refereeStatsRepository.findByRefereeIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());

        List<FixtureContext> result = contextBuilder.buildContextsForFixtures(fixtures);

        assertThat(result).hasSize(2);
        
        verify(fixtureOddsRepository, times(1)).findAllById(anyCollection());
        verify(fixturePotentialsRepository, times(1)).findAllById(anyCollection());
        verify(leagueRepository, times(1)).findAllById(anyCollection());
        verify(teamRepository, times(1)).findAllById(anyCollection());
        verify(teamSeasonStatsRepository, times(1)).findByTeamIdsAndSeasonIds(anyCollection(), anyCollection());
        verify(teamRecentFormRepository, times(1)).findByTeamIdIn(anyCollection());
        verify(refereeStatsRepository, times(1)).findByRefereeIdsAndSeasonIds(anyCollection(), anyCollection());
        verify(playerSeasonStatsRepository, times(1)).findByTeamIdsAndSeasonIds(anyCollection(), anyCollection());
    }

    @Test
    void buildContextsForFixtures_correctlyMapsDataToContexts() {
        Fixture fixture = createFixture(1L, 100L, 10L, 11L, 50L);
        List<Fixture> fixtures = List.of(fixture);

        FixtureOdds odds = FixtureOdds.builder().fixtureId(1L).oddsFt1(1.5).build();
        FixturePotentials potentials = FixturePotentials.builder().fixtureId(1L).bttsPotential(70.0).build();
        League league = League.builder().currentSeasonId(100L).name("Test League").build();
        Team homeTeam = createTeam(10L, "Home Team");
        Team awayTeam = createTeam(11L, "Away Team");
        TeamSeasonStats homeStats = createTeamStats(10L, 100L);
        TeamSeasonStats awayStats = createTeamStats(11L, 100L);
        TeamRecentForm homeForm = TeamRecentForm.builder().teamId(10L).build();
        TeamRecentForm awayForm = TeamRecentForm.builder().teamId(11L).build();
        RefereeStats refereeStats = RefereeStats.builder().refereeId(50L).seasonId(100L).build();

        when(fixtureOddsRepository.findAllById(anyCollection())).thenReturn(List.of(odds));
        when(fixturePotentialsRepository.findAllById(anyCollection())).thenReturn(List.of(potentials));
        when(leagueRepository.findAllById(anyCollection())).thenReturn(List.of(league));
        when(teamRepository.findAllById(anyCollection())).thenReturn(List.of(homeTeam, awayTeam));
        when(teamSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(List.of(homeStats, awayStats));
        when(teamRecentFormRepository.findByTeamIdIn(anyCollection())).thenReturn(List.of(homeForm, awayForm));
        when(refereeStatsRepository.findByRefereeIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(List.of(refereeStats));
        when(playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());

        List<FixtureContext> result = contextBuilder.buildContextsForFixtures(fixtures);

        assertThat(result).hasSize(1);
        FixtureContext context = result.get(0);
        
        assertThat(context.getFixture()).isEqualTo(fixture);
        assertThat(context.getOdds()).isEqualTo(odds);
        assertThat(context.getPotentials()).isEqualTo(potentials);
        assertThat(context.getLeague()).isEqualTo(league);
        assertThat(context.getHomeTeam()).isEqualTo(homeTeam);
        assertThat(context.getAwayTeam()).isEqualTo(awayTeam);
        assertThat(context.getHomeTeamStats()).isEqualTo(homeStats);
        assertThat(context.getAwayTeamStats()).isEqualTo(awayStats);
        assertThat(context.getHomeTeamForm()).isEqualTo(homeForm);
        assertThat(context.getAwayTeamForm()).isEqualTo(awayForm);
        assertThat(context.getRefereeStats()).isEqualTo(refereeStats);
    }

    @Test
    void buildContextsForFixtures_filtersIncompleteContexts() {
        Fixture completeFixture = createFixture(1L, 100L, 10L, 11L, null);
        Fixture incompleteFixture = createFixture(2L, 100L, null, null, null);
        List<Fixture> fixtures = List.of(completeFixture, incompleteFixture);

        Team homeTeam = createTeam(10L, "Home Team");
        Team awayTeam = createTeam(11L, "Away Team");
        TeamSeasonStats homeStats = createTeamStats(10L, 100L);
        TeamSeasonStats awayStats = createTeamStats(11L, 100L);

        when(fixtureOddsRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(fixturePotentialsRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(leagueRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
        when(teamRepository.findAllById(anyCollection())).thenReturn(List.of(homeTeam, awayTeam));
        when(teamSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(List.of(homeStats, awayStats));
        when(teamRecentFormRepository.findByTeamIdIn(anyCollection())).thenReturn(Collections.emptyList());
        when(playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());

        List<FixtureContext> result = contextBuilder.buildContextsForFixtures(fixtures);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFixture().getId()).isEqualTo(1L);
    }

    @Test
    void buildContext_singleFixture_usesIndividualQueries() {
        Fixture fixture = createFixture(1L, 100L, 10L, 11L, 50L);
        
        FixtureOdds odds = FixtureOdds.builder().fixtureId(1L).build();
        Team homeTeam = createTeam(10L, "Home");
        Team awayTeam = createTeam(11L, "Away");
        TeamSeasonStats homeStats = createTeamStats(10L, 100L);
        TeamSeasonStats awayStats = createTeamStats(11L, 100L);
        
        when(fixtureOddsRepository.findById(1L)).thenReturn(Optional.of(odds));
        when(fixturePotentialsRepository.findById(1L)).thenReturn(Optional.empty());
        when(leagueRepository.findById(100L)).thenReturn(Optional.empty());
        when(teamRepository.findById(10L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(11L)).thenReturn(Optional.of(awayTeam));
        when(teamSeasonStatsRepository.findByTeamIdAndSeasonId(10L, 100L)).thenReturn(Optional.of(homeStats));
        when(teamSeasonStatsRepository.findByTeamIdAndSeasonId(11L, 100L)).thenReturn(Optional.of(awayStats));
        when(teamRecentFormRepository.findByTeamId(10L)).thenReturn(Optional.empty());
        when(teamRecentFormRepository.findByTeamId(11L)).thenReturn(Optional.empty());
        when(refereeStatsRepository.findByRefereeIdAndSeasonId(50L, 100L)).thenReturn(Optional.empty());
        when(playerSeasonStatsRepository.findByTeamIdsAndSeasonIds(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyList());

        FixtureContext result = contextBuilder.buildContext(fixture);

        assertThat(result.getFixture()).isEqualTo(fixture);
        assertThat(result.getOdds()).isEqualTo(odds);
        assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
        assertThat(result.getAwayTeam()).isEqualTo(awayTeam);
        assertThat(result.getHomeTeamStats()).isEqualTo(homeStats);
        assertThat(result.getAwayTeamStats()).isEqualTo(awayStats);
    }

    private Fixture createFixture(Long id, Long seasonId, Long homeTeamId, Long awayTeamId, Long refereeId) {
        return Fixture.builder()
                .id(id)
                .seasonId(seasonId)
                .homeTeamId(homeTeamId)
                .awayTeamId(awayTeamId)
                .refereeId(refereeId)
                .homeTeamName("Home " + id)
                .awayTeamName("Away " + id)
                .status("incomplete")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }

    private TeamSeasonStats createTeamStats(Long teamId, Long seasonId) {
        return TeamSeasonStats.builder()
                .teamId(teamId)
                .seasonId(seasonId)
                .matchesPlayed(20)
                .build();
    }
}
