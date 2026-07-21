package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixture_potentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixturePotentials {

    @Id
    private Long fixtureId;

    private Double bttsPotential;

    private Double o15Potential;
    private Double o25Potential;
    private Double o35Potential;
    private Double o45Potential;

    private Double o05HtPotential;
    private Double o15HtPotential;

    private Double u15Potential;

    private Double avgPotential;

    private Double cornersPotential;
    private Double cornersO85Potential;
    private Double cornersO95Potential;
    private Double cornersO105Potential;

    private Double cardsPotential;

    private Double offsidesPotential;
}
