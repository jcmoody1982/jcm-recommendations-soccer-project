package com.jcm.recommendations.soccer.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "footystats.api")
@Data
public class FootyStatsApiConfig {

    private String baseUrl = "https://api.football-data-api.com";
    private String key;
    private int timeoutSeconds = 30;
    private int maxRetries = 3;
}
