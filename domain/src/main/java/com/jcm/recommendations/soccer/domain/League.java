package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "league")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {

    @Id
    private Long currentSeasonId;

    @NotBlank
    private String name;

    private String country;

    private String image;
}
