package com.jcm.recommendations.soccer.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.jcm.recommendations.soccer")
public class SoccerRecommendationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoccerRecommendationsApplication.class, args);
    }
}
