package com.jcm.recommendations.soccer.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CountryGroupDto {
    private String country;
    private String countryCode;
    private List<CompetitionDto> competitions;
}
