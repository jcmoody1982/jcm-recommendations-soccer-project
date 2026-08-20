package com.jcm.recommendations.soccer.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MediaProxyControllerTest {

    private final MediaProxyController controller = new MediaProxyController();

    @Test
    void rejectsNonHttpsUrls() {
        ResponseEntity<byte[]> response = controller.proxyImage("http://cdn.footystats.org/logo.png");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsDisallowedHosts() {
        ResponseEntity<byte[]> response = controller.proxyImage("https://evil.example/logo.png");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
