package com.jcm.recommendations.soccer.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.jcm.recommendations.soccer")
@ConfigurationPropertiesScan(basePackages = "com.jcm.recommendations.soccer.core.config")
@EnableScheduling
public class SoccerRecommendationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoccerRecommendationsApplication.class, args);
    }
}
