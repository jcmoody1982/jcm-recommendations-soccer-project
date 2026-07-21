package com.jcm.recommendations.soccer.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SoccerRecommendationsApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    void leagueServiceBeanExists() {
        assertNotNull(context.getBean("leagueService"));
    }

    @Test
    void fixtureServiceBeanExists() {
        assertNotNull(context.getBean("fixtureService"));
    }

    @Test
    void teamServiceBeanExists() {
        assertNotNull(context.getBean("teamService"));
    }
}
