package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "recommendation_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_snapshot_date_fixture_type",
                columnNames = {"snapshot_date", "fixture_id", "type"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    private Long homeTeamId;
    private Long awayTeamId;
    private String homeTeamName;
    private String awayTeamName;

    private Long matchDateUnix;

    private Long leagueId;
    private String leagueName;
    private String leagueImage;

    /** RecommendationType enum name from core engines. */
    @Column(nullable = false, length = 64)
    private String type;

    private String market;

    /** ConfidenceLevel enum name (STRONG / MODERATE). */
    @Column(length = 32)
    private String confidence;

    private Double score;
    private Double odds;

    /**
     * UC-037: 1-based Elite rank for this snapshot day (null when not Elite).
     * At most one Elite pick per fixture; top 10 Strong %-style picks.
     */
    private Integer eliteRank;

    @Column(length = 2000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String factorsJson;

    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PickOutcome outcome = PickOutcome.PENDING;

    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String matchResultJson;
}
