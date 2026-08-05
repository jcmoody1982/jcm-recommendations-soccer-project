package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.web.config.BetaAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final BetaAuthProperties betaAuthProperties;
    private final SecurityContextRepository securityContextRepository;

    public record LoginRequest(@NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (!betaAuthProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "role", "OPEN",
                    "authEnabled", false));
        }

        String password = request.password() == null ? "" : request.password().trim();
        String role = resolveRole(password);
        if (role == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "authenticated", false,
                    "error", "Invalid password"));
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                role.toLowerCase(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "role", role,
                "authEnabled", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("authenticated", false));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (!betaAuthProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "role", "OPEN",
                    "authEnabled", false));
        }
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "authEnabled", true));
        }
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("BETA");
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "role", role,
                "authEnabled", true));
    }

    private String resolveRole(String password) {
        if (matches(betaAuthProperties.getAdminPassword(), password)) {
            return "ADMIN";
        }
        if (matches(betaAuthProperties.getSitePassword(), password)) {
            return "BETA";
        }
        return null;
    }

    private static boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
