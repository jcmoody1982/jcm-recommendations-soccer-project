package com.jcm.recommendations.soccer.web.service;

import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.core.repository.LeagueRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.League;
import com.jcm.recommendations.soccer.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueOverviewService {

    private final LeagueRepository leagueRepository;
    private final FixtureRepository fixtureRepository;

    public LeagueOverviewResponse getLeagueOverview() {
        log.info("Generating league overview");

        List<League> leagues = leagueRepository.findAll();
        long nowUnix = Instant.now().getEpochSecond();
        List<Fixture> upcomingFixtures = fixtureRepository.findUpcomingFixtures(nowUnix);

        log.debug("Found {} leagues and {} upcoming fixtures", leagues.size(), upcomingFixtures.size());

        Map<Long, League> leagueMap = leagues.stream()
                .collect(Collectors.toMap(League::getCurrentSeasonId, l -> l));

        Map<Long, List<Fixture>> fixturesByLeague = upcomingFixtures.stream()
                .collect(Collectors.groupingBy(Fixture::getSeasonId));

        Map<String, List<CompetitionDto>> competitionsByCountry = new TreeMap<>();

        for (League league : leagues) {
            String country = league.getCountry() != null ? league.getCountry() : "Unknown";
            List<Fixture> leagueFixtures = fixturesByLeague.getOrDefault(league.getCurrentSeasonId(), Collections.emptyList());

            List<FixtureSummaryDto> fixtureSummaries = leagueFixtures.stream()
                    .map(f -> FixtureSummaryDto.builder()
                            .fixtureId(f.getId())
                            .homeTeam(f.getHomeTeamName())
                            .awayTeam(f.getAwayTeamName())
                            .matchDate(Instant.ofEpochSecond(f.getDateUnix()))
                            .build())
                    .sorted(Comparator.comparing(FixtureSummaryDto::getMatchDate))
                    .toList();

            CompetitionDto competition = CompetitionDto.builder()
                    .leagueId(league.getCurrentSeasonId())
                    .name(league.getName())
                    .logoUrl(league.getImage())
                    .fixtureCount(fixtureSummaries.size())
                    .fixtures(fixtureSummaries)
                    .build();

            competitionsByCountry
                    .computeIfAbsent(country, k -> new ArrayList<>())
                    .add(competition);
        }

        List<CountryGroupDto> countryGroups = competitionsByCountry.entrySet().stream()
                .map(entry -> CountryGroupDto.builder()
                        .country(entry.getKey())
                        .countryCode(getCountryCode(entry.getKey()))
                        .competitions(entry.getValue())
                        .build())
                .toList();

        int totalFixtures = upcomingFixtures.size();

        log.info("League overview generated: {} countries, {} total fixtures", countryGroups.size(), totalFixtures);

        return LeagueOverviewResponse.builder()
                .countries(countryGroups)
                .totalFixtures(totalFixtures)
                .lastUpdated(Instant.now())
                .build();
    }

    private String getCountryCode(String country) {
        return switch (country.toLowerCase()) {
            case "england" -> "GB-ENG";
            case "germany" -> "DE";
            case "spain" -> "ES";
            case "italy" -> "IT";
            case "france" -> "FR";
            case "netherlands" -> "NL";
            case "portugal" -> "PT";
            case "belgium" -> "BE";
            case "scotland" -> "GB-SCT";
            case "turkey" -> "TR";
            case "greece" -> "GR";
            case "austria" -> "AT";
            case "switzerland" -> "CH";
            case "denmark" -> "DK";
            case "norway" -> "NO";
            case "sweden" -> "SE";
            case "russia" -> "RU";
            case "ukraine" -> "UA";
            case "poland" -> "PL";
            case "czech republic" -> "CZ";
            case "brazil" -> "BR";
            case "argentina" -> "AR";
            case "mexico" -> "MX";
            case "usa", "united states" -> "US";
            case "australia" -> "AU";
            case "japan" -> "JP";
            case "china" -> "CN";
            case "south korea" -> "KR";
            default -> country.length() >= 2 ? country.substring(0, 2).toUpperCase() : "XX";
        };
    }
}
