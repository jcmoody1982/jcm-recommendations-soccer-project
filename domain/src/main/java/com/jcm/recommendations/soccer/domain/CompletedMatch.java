package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "completed_match")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletedMatch {

    @Id
    private Long fixtureId;

    @Column(length = 32)
    private String status;

    private Integer homeGoals;
    private Integer awayGoals;

    private Integer htHomeGoals;
    private Integer htAwayGoals;

    private Integer secondHalfHomeGoals;
    private Integer secondHalfAwayGoals;

    private Integer homeCorners;
    private Integer awayCorners;

    private Integer homeYellowCards;
    private Integer awayYellowCards;
    private Integer homeRedCards;
    private Integer awayRedCards;

    /** Calendar date used when this row was last fetched via todays-matches. */
    private LocalDate sourceDate;

    private Instant fetchedAt;

    /** JSON array of player goal events from /match ({@code team_a/b_goal_details}). Null until fetched. */
    @Column(columnDefinition = "TEXT")
    private String goalEventsJson;
}
