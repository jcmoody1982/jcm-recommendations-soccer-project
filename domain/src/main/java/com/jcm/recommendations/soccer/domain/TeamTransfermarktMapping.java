package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "team_transfermarkt_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamTransfermarktMapping {

    @Id
    private Long teamId;

    private Long transfermarktClubId;

    /** MANUAL, CSV, or NAME_SEARCH */
    private String matchMethod;

    /** HIGH or LOW — only HIGH/MANUAL rows drive engine factors. */
    private String confidence;

    private Instant verifiedAt;
}
