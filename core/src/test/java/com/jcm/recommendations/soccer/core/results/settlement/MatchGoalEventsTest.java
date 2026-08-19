package com.jcm.recommendations.soccer.core.results.settlement;

import com.jcm.recommendations.soccer.core.client.dto.GoalDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchGoalEventsTest {

    @Test
    void roundTripsScorersAndAssistsAndDropsOwnGoals() {
        GoalDetailDto goal = new GoalDetailDto();
        goal.setPlayerId(8298L);
        goal.setAssistPlayerId(4281L);
        goal.setTime("17");

        GoalDetailDto ownGoal = new GoalDetailDto();
        ownGoal.setPlayerId(99L);
        ownGoal.setAssistPlayerId(12L);
        ownGoal.setExtra("Own Goal");
        ownGoal.setTime("70");

        List<MatchGoalEvents.StoredGoalEvent> events = MatchGoalEvents.fromDetails(List.of(goal, ownGoal), List.of());
        String json = MatchGoalEvents.toJson(events);
        List<MatchGoalEvents.StoredGoalEvent> parsed = MatchGoalEvents.parse(json);

        assertThat(MatchGoalEvents.scoringPlayerIds(parsed)).containsExactly(8298L);
        assertThat(MatchGoalEvents.assistingPlayerIds(parsed)).containsExactly(4281L);
        assertThat(MatchGoalEvents.parse(null)).isNull();
    }
}
