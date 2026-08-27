package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.repository.TeamTransfermarktMappingRepository;
import com.jcm.recommendations.soccer.core.transfermarkt.config.TransfermarktProperties;
import com.jcm.recommendations.soccer.domain.TeamTransfermarktMapping;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamTransfermarktMappingService {

    static final String CONFIDENCE_HIGH = "HIGH";
    static final String CONFIDENCE_LOW = "LOW";
    static final String METHOD_MANUAL = "MANUAL";
    static final String METHOD_CSV = "CSV";

    private final TeamTransfermarktMappingRepository mappingRepository;
    private final TransfermarktProperties properties;

    @PostConstruct
    void loadSeedMappings() {
        importMappingsFromClasspath(false);
    }

    @Transactional
    public int importMappingsFromClasspath(boolean overwriteExisting) {
        String resourcePath = properties.getMappingResource();
        if (!StringUtils.hasText(resourcePath)) {
            return 0;
        }

        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.debug("Transfermarkt mapping resource not found: {}", resourcePath);
            return 0;
        }

        int imported = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSkipped) {
                    headerSkipped = true;
                    if (line.toLowerCase(Locale.ROOT).startsWith("footystats_team_id")) {
                        continue;
                    }
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 4) {
                    log.warn("Skipping invalid Transfermarkt mapping row: {}", line);
                    continue;
                }

                Long teamId = parseLong(parts[0]);
                Long clubId = parseLong(parts[1]);
                if (teamId == null || clubId == null) {
                    continue;
                }

                String method = normalize(parts[2], METHOD_CSV);
                String confidence = normalizeConfidence(parts[3]);

                Optional<TeamTransfermarktMapping> existing = mappingRepository.findById(teamId);
                if (existing.isPresent() && !overwriteExisting) {
                    continue;
                }

                TeamTransfermarktMapping mapping = TeamTransfermarktMapping.builder()
                        .teamId(teamId)
                        .transfermarktClubId(clubId)
                        .matchMethod(method)
                        .confidence(confidence)
                        .verifiedAt(isEngineUsableConfidence(confidence) ? Instant.now() : null)
                        .build();
                mappingRepository.save(mapping);
                imported++;
            }
        } catch (Exception e) {
            log.warn("Failed to import Transfermarkt mappings from {}: {}", resourcePath, e.getMessage());
        }

        if (imported > 0) {
            log.info("Imported Transfermarkt team mappings from CSV: count={}", imported);
        }
        return imported;
    }

    public Optional<TeamTransfermarktMapping> findMapping(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return mappingRepository.findById(teamId);
    }

    public boolean isEngineUsable(TeamTransfermarktMapping mapping) {
        if (mapping == null || mapping.getTransfermarktClubId() == null) {
            return false;
        }
        if (METHOD_MANUAL.equalsIgnoreCase(mapping.getMatchMethod())) {
            return true;
        }
        return isEngineUsableConfidence(mapping.getConfidence());
    }

    static boolean isEngineUsableConfidence(String confidence) {
        if (!StringUtils.hasText(confidence)) {
            return false;
        }
        return CONFIDENCE_HIGH.equals(confidence.trim().toUpperCase(Locale.ROOT));
    }

    private static String normalizeConfidence(String raw) {
        if (!StringUtils.hasText(raw)) {
            return CONFIDENCE_LOW;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return CONFIDENCE_HIGH.equals(normalized) ? CONFIDENCE_HIGH : CONFIDENCE_LOW;
    }

    private static String normalize(String raw, String defaultValue) {
        return StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : defaultValue;
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
