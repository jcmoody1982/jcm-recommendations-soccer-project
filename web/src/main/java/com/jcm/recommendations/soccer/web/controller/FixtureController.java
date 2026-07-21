package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fixtures")
@RequiredArgsConstructor
public class FixtureController {

    private final FixtureService fixtureService;

    @GetMapping
    public ResponseEntity<List<Fixture>> getUpcomingFixtures(
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false, defaultValue = "7") int daysAhead) {

        List<Fixture> fixtures;
        if (seasonId != null) {
            fixtures = fixtureService.getFixturesBySeasonId(seasonId);
        } else {
            fixtures = fixtureService.getUpcomingFixtures(daysAhead);
        }
        return ResponseEntity.ok(fixtures);
    }

    @GetMapping("/{fixtureId}")
    public ResponseEntity<Fixture> getFixture(@PathVariable Long fixtureId) {
        Fixture fixture = fixtureService.getFixtureById(fixtureId);
        if (fixture == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(fixture);
    }

    @GetMapping("/{fixtureId}/odds")
    public ResponseEntity<FixtureOdds> getFixtureOdds(@PathVariable Long fixtureId) {
        FixtureOdds odds = fixtureService.getFixtureOdds(fixtureId);
        if (odds == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(odds);
    }

    @GetMapping("/{fixtureId}/potentials")
    public ResponseEntity<FixturePotentials> getFixturePotentials(@PathVariable Long fixtureId) {
        FixturePotentials potentials = fixtureService.getFixturePotentials(fixtureId);
        if (potentials == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(potentials);
    }
}
