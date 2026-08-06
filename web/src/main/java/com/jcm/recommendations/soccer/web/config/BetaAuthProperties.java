package com.jcm.recommendations.soccer.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "beta.auth")
public class BetaAuthProperties {

    /** When false, all API routes are open (local convenience). */
    private boolean enabled = true;

    /** Shared password for site access (ROLE_BETA). */
    private String sitePassword = "";

    /** Separate password for /api/admin (ROLE_ADMIN; also unlocks the site). */
    private String adminPassword = "";
}
