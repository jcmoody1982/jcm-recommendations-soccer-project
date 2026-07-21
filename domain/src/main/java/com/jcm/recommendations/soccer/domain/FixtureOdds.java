package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixture_odds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixtureOdds {

    @Id
    private Long fixtureId;

    private Double oddsFt1;
    private Double oddsFtX;
    private Double oddsFt2;

    private Double oddsFtOver05;
    private Double oddsFtOver15;
    private Double oddsFtOver25;
    private Double oddsFtOver35;
    private Double oddsFtOver45;

    private Double oddsFtUnder05;
    private Double oddsFtUnder15;
    private Double oddsFtUnder25;
    private Double oddsFtUnder35;
    private Double oddsFtUnder45;

    private Double oddsBttsYes;
    private Double oddsBttsNo;
}
