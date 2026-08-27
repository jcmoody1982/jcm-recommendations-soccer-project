package com.jcm.recommendations.soccer.core.recommendation.model;

import com.jcm.recommendations.soccer.domain.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FixtureContext {
    
    private Fixture fixture;
    private FixtureOdds odds;
    private FixturePotentials potentials;
    private FixtureHeadToHead headToHead;
    
    private League league;
    
    private Team homeTeam;
    private Team awayTeam;
    
    private TeamSeasonStats homeTeamStats;
    private TeamSeasonStats awayTeamStats;
    
    private TeamRecentForm homeTeamForm;
    private TeamRecentForm awayTeamForm;
    
    private RefereeStats refereeStats;

    private List<PlayerSeasonStats> homePlayers;
    private List<PlayerSeasonStats> awayPlayers;
    
    public boolean hasCompleteData() {
        return fixture != null 
            && homeTeam != null 
            && awayTeam != null
            && homeTeamStats != null 
            && awayTeamStats != null;
    }
    
    public boolean hasRecentForm() {
        return homeTeamForm != null && awayTeamForm != null;
    }
    
    public boolean hasOdds() {
        return odds != null;
    }
    
    public boolean hasPotentials() {
        return potentials != null;
    }

    public boolean hasHeadToHead() {
        return headToHead != null
                && headToHead.getPreviousMeetings() != null
                && headToHead.getPreviousMeetings() > 0;
    }
    
    public boolean hasRefereeStats() {
        return refereeStats != null;
    }

    public boolean hasPlayerStats() {
        return !isEmpty(homePlayers) || !isEmpty(awayPlayers);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
