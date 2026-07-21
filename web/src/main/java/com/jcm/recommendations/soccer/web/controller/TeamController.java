package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.service.TeamFormService;
import com.jcm.recommendations.soccer.core.service.TeamService;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final TeamFormService teamFormService;

    @GetMapping
    public ResponseEntity<List<Team>> getTeams(
            @RequestParam(required = false) Long seasonId) {

        List<Team> teams;
        if (seasonId != null) {
            teams = teamService.getTeamsBySeasonId(seasonId);
        } else {
            teams = teamService.getAllTeams();
        }
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<Team> getTeam(@PathVariable Long teamId) {
        Team team = teamService.getTeamById(teamId);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(team);
    }

    @GetMapping("/{teamId}/stats")
    public ResponseEntity<TeamSeasonStats> getTeamStats(
            @PathVariable Long teamId,
            @RequestParam Long seasonId) {

        TeamSeasonStats stats = teamService.getTeamSeasonStats(teamId, seasonId);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{teamId}/form")
    public ResponseEntity<TeamRecentForm> getTeamForm(@PathVariable Long teamId) {
        TeamRecentForm form = teamFormService.getTeamRecentForm(teamId);
        if (form == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(form);
    }
}
