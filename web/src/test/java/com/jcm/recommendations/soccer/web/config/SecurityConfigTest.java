package com.jcm.recommendations.soccer.web.config;

import com.jcm.recommendations.soccer.core.config.JacksonConfig;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.repository.FixtureOddsRepository;
import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.core.results.RecommendationSnapshotService;
import com.jcm.recommendations.soccer.core.results.ResultsMatchIngestService;
import com.jcm.recommendations.soccer.core.results.ResultsPerformanceService;
import com.jcm.recommendations.soccer.core.results.SettlementService;
import com.jcm.recommendations.soccer.core.service.DataSyncService;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.web.controller.AdminController;
import com.jcm.recommendations.soccer.web.controller.AuthController;
import com.jcm.recommendations.soccer.web.controller.RecommendationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RecommendationController.class, AdminController.class, AuthController.class})
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = {
        "beta.auth.enabled=true",
        "beta.auth.site-password=site-secret",
        "beta.auth.admin-password=admin-secret"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;
    @MockitoBean
    private DataSyncService dataSyncService;
    @MockitoBean
    private FixtureService fixtureService;
    @MockitoBean
    private FixtureRepository fixtureRepository;
    @MockitoBean
    private FixtureOddsRepository fixtureOddsRepository;
    @MockitoBean
    private RecommendationSnapshotService recommendationSnapshotService;
    @MockitoBean
    private ResultsMatchIngestService resultsMatchIngestService;
    @MockitoBean
    private SettlementService settlementService;
    @MockitoBean
    private ResultsPerformanceService resultsPerformanceService;

    @Test
    void anonymousApiRequestIsRejectedWithJson401() throws Exception {
        mockMvc.perform(get("/api/recommendations/grouped"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Login required"));
    }

    @Test
    void betaUserCanReadRecommendations() throws Exception {
        when(recommendationService.getRecommendationsGroupedByType(anyDouble())).thenReturn(Map.of());

        mockMvc.perform(get("/api/recommendations/grouped").with(user("beta").roles("BETA")))
                .andExpect(status().isOk());
    }

    @Test
    void betaUserIsForbiddenFromAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/admin/cache/evict").with(user("beta").roles("BETA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void anonymousAdminRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/cache/evict"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanUseAdminEndpointsAndTheSite() throws Exception {
        when(recommendationService.getRecommendationsGroupedByType(anyDouble())).thenReturn(Map.of());

        mockMvc.perform(post("/api/admin/cache/evict").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
        mockMvc.perform(get("/api/recommendations/grouped").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.authEnabled").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"site-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BETA"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void corsPreflightFromAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/recommendations/grouped")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
