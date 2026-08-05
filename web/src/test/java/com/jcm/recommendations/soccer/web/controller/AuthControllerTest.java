package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.web.config.BetaAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private SecurityContextRepository securityContextRepository;

    private BetaAuthProperties properties;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        properties = new BetaAuthProperties();
        properties.setEnabled(true);
        properties.setSitePassword("site-secret");
        properties.setAdminPassword("admin-secret");
        controller = new AuthController(properties, securityContextRepository);
    }

    @Test
    void loginWithSitePassword() {
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Map<String, Object>> result = controller.login(
                new AuthController.LoginRequest("site-secret"), request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).containsEntry("authenticated", true);
        assertThat(result.getBody()).containsEntry("role", "BETA");
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    void loginWithAdminPassword() {
        ResponseEntity<Map<String, Object>> result = controller.login(
                new AuthController.LoginRequest("admin-secret"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).containsEntry("role", "ADMIN");
    }

    @Test
    void loginWithWrongPassword() {
        ResponseEntity<Map<String, Object>> result = controller.login(
                new AuthController.LoginRequest("nope"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).containsEntry("authenticated", false);
    }

    @Test
    void meWhenUnauthenticated() {
        ResponseEntity<Map<String, Object>> result = controller.me(null);
        assertThat(result.getBody()).containsEntry("authenticated", false);
        assertThat(result.getBody()).containsEntry("authEnabled", true);
    }
}
