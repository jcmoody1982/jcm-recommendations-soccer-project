package com.jcm.recommendations.soccer.core.results.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.client.dto.GoalDetailDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Player goal/assist events from FootyStats {@code team_a_goal_details} / {@code team_b_goal_details}.
 */
public final class MatchGoalEvents {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<StoredGoalEvent>> EVENT_LIST = new TypeReference<>() {};

    private MatchGoalEvents() {
    }

    public record StoredGoalEvent(Long playerId, Long assistPlayerId, String extra, String time) {}

    public static List<StoredGoalEvent> fromDetails(List<GoalDetailDto> home, List<GoalDetailDto> away) {
        List<StoredGoalEvent> events = new ArrayList<>();
        append(events, home);
        append(events, away);
        return events;
    }

    public static String toJson(List<StoredGoalEvent> events) {
        try {
            return MAPPER.writeValueAsString(events);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize goal events", e);
        }
    }

    public static List<StoredGoalEvent> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<StoredGoalEvent> events = MAPPER.readValue(json, EVENT_LIST);
            return events != null ? events : List.of();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static Set<Long> scoringPlayerIds(List<StoredGoalEvent> events) {
        Set<Long> ids = new LinkedHashSet<>();
        if (events == null) {
            return ids;
        }
        for (StoredGoalEvent event : events) {
            if (isOwnGoal(event.extra())) {
                continue;
            }
            Long playerId = positiveId(event.playerId());
            if (playerId != null) {
                ids.add(playerId);
            }
        }
        return ids;
    }

    public static Set<Long> assistingPlayerIds(List<StoredGoalEvent> events) {
        Set<Long> ids = new LinkedHashSet<>();
        if (events == null) {
            return ids;
        }
        for (StoredGoalEvent event : events) {
            if (isOwnGoal(event.extra())) {
                continue;
            }
            Long assistId = positiveId(event.assistPlayerId());
            if (assistId != null) {
                ids.add(assistId);
            }
        }
        return ids;
    }

    public static boolean isOwnGoal(String extra) {
        return extra != null && extra.toLowerCase(Locale.ROOT).contains("own");
    }

    public static Long positiveId(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static void append(List<StoredGoalEvent> events, List<GoalDetailDto> details) {
        if (details == null) {
            return;
        }
        for (GoalDetailDto detail : details) {
            if (detail == null) {
                continue;
            }
            events.add(new StoredGoalEvent(
                    positiveId(detail.getPlayerId()),
                    positiveId(detail.getAssistPlayerId()),
                    detail.getExtra(),
                    detail.getTime()));
        }
    }
}
