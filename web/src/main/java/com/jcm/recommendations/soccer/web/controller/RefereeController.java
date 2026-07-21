package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.service.RefereeService;
import com.jcm.recommendations.soccer.domain.Referee;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/referees")
@RequiredArgsConstructor
public class RefereeController {

    private final RefereeService refereeService;

    @GetMapping
    public ResponseEntity<List<Referee>> getReferees(
            @RequestParam(required = false) Long seasonId) {

        List<Referee> referees;
        if (seasonId != null) {
            referees = refereeService.getRefereesBySeasonId(seasonId);
        } else {
            referees = refereeService.getAllReferees();
        }
        return ResponseEntity.ok(referees);
    }

    @GetMapping("/{refereeId}")
    public ResponseEntity<Referee> getReferee(@PathVariable Long refereeId) {
        Referee referee = refereeService.getRefereeById(refereeId);
        if (referee == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(referee);
    }

    @GetMapping("/{refereeId}/stats")
    public ResponseEntity<RefereeStats> getRefereeStats(
            @PathVariable Long refereeId,
            @RequestParam Long seasonId) {

        RefereeStats stats = refereeService.getRefereeStats(refereeId, seasonId);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }
}
