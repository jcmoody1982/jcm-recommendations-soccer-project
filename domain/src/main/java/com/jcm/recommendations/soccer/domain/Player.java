package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "player")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    private Long id;

    private String knownAs;
    private String fullName;
    private String firstName;
    private String lastName;
    private String position;
    private String nationality;

    private Long seasonId;
}
