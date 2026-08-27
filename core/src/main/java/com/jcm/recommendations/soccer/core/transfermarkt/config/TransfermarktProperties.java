package com.jcm.recommendations.soccer.core.transfermarkt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "transfermarkt")
@Data
public class TransfermarktProperties {

    private boolean enabled = false;

    /** Base URL for TM-derived API (e.g. Parse.bot proxy or internal sidecar). */
    private String baseUrl = "";

    private String apiKey = "";

    /** Skip re-fetch when profile is newer than this many days. */
    private int freshnessDays = 7;

    private long rateLimitMs = 300L;

    /** TM season start year (e.g. 2025 for 2025/26). Empty = current UTC year. */
    private String season = "";

    private String mappingResource = "transfermarkt/team-mapping.csv";
}
