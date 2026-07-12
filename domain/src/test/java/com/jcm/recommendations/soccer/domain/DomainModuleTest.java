package com.jcm.recommendations.soccer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DomainModuleTest {

    @Test
    void nameIsDomain() {
        assertEquals("domain", DomainModule.name());
    }
}
