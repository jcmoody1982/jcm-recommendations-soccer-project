package com.jcm.recommendations.soccer.web.service;

import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.core.repository.LeagueRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.League;
import com.jcm.recommendations.soccer.web.dto.CompetitionDto;
import com.jcm.recommendations.soccer.web.dto.CountryGroupDto;
import com.jcm.recommendations.soccer.web.dto.FixtureSummaryDto;
import com.jcm.recommendations.soccer.web.dto.LeagueOverviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueOverviewServiceTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private FixtureRepository fixtureRepository;

    @InjectMocks
    private LeagueOverviewService service;

    @Test
    void groupsCompetitionsByCountryAlphabeticallyWithSortedFixtures() {
        League epl = League.builder().currentSeasonId(1L).name("Premier League").country("England").build();
        League bundesliga = League.builder().currentSeasonId(2L).name("Bundesliga").country("Germany").build();
        League mystery = League.builder().currentSeasonId(3L).name("Mystery League").build();
        Fixture late = Fixture.builder().id(11L).seasonId(1L).homeTeamName("A").awayTeamName("B").dateUnix(2_000L).build();
        Fixture early = Fixture.builder().id(10L).seasonId(1L).homeTeamName("C").awayTeamName("D").dateUnix(1_000L).build();
        when(leagueRepository.findAll()).thenReturn(List.of(bundesliga, epl, mystery));
        when(fixtureRepository.findUpcomingFixtures(anyLong())).thenReturn(List.of(late, early));

        LeagueOverviewResponse response = service.getLeagueOverview();

        assertThat(response.getTotalFixtures()).isEqualTo(2);
        assertThat(response.getCountries()).extracting(CountryGroupDto::getCountry)
                .containsExactly("England", "Germany", "Unknown");
        assertThat(response.getCountries()).extracting(CountryGroupDto::getCountryCode)
                .containsExactly("GB-ENG", "DE", "UN");

        CompetitionDto eplDto = response.getCountries().getFirst().getCompetitions().getFirst();
        assertThat(eplDto.getFixtureCount()).isEqualTo(2);
        assertThat(eplDto.getFixtures()).extracting(FixtureSummaryDto::getFixtureId).containsExactly(10L, 11L);

        CompetitionDto bundesligaDto = response.getCountries().get(1).getCompetitions().getFirst();
        assertThat(bundesligaDto.getFixtureCount()).isZero();
    }

    @Test
    void emptyDatabaseGivesEmptyOverview() {
        when(leagueRepository.findAll()).thenReturn(List.of());
        when(fixtureRepository.findUpcomingFixtures(anyLong())).thenReturn(List.of());

        LeagueOverviewResponse response = service.getLeagueOverview();

        assertThat(response.getCountries()).isEmpty();
        assertThat(response.getTotalFixtures()).isZero();
        assertThat(response.getLastUpdated()).isNotNull();
    }
}
