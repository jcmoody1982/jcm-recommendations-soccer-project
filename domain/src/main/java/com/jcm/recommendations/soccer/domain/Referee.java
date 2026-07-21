package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "referee")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referee {

    @Id
    private Long id;

    private String fullName;
    private String firstName;
    private String lastName;
    private String knownAs;

    private Long seasonId;
}
