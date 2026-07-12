package com.jcm.recommendations.soccer.core;

import org.springframework.stereotype.Service;

import com.jcm.recommendations.soccer.domain.DomainModule;

@Service
public class RecommendationService {

    public String moduleSummary() {
        return "core+" + DomainModule.name();
    }
}
