package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.service.LeagueService;
import com.jcm.recommendations.soccer.domain.League;
import com.jcm.recommendations.soccer.web.dto.LeagueOverviewResponse;
import com.jcm.recommendations.soccer.web.service.LeagueOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueService leagueService;
    private final LeagueOverviewService leagueOverviewService;

    @GetMapping
    public ResponseEntity<List<League>> getAllLeagues() {
        List<League> leagues = leagueService.getAllLeagues();
        return ResponseEntity.ok(leagues);
    }

    @GetMapping("/overview")
    public ResponseEntity<LeagueOverviewResponse> getLeagueOverview() {
        LeagueOverviewResponse overview = leagueOverviewService.getLeagueOverview();
        return ResponseEntity.ok(overview);
    }
}
