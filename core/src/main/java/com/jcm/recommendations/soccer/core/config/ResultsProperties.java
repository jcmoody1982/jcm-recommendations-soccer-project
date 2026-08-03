package com.jcm.recommendations.soccer.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
@ConfigurationProperties(prefix = "results")
@Data
public class ResultsProperties {

    /** Brand timezone for snapshot "today" and results jobs. */
    private String timezone = "Europe/London";

    /** Days to keep retrying PENDING snapshots before VOID expiry. */
    private int pendingLookbackDays = 7;

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
