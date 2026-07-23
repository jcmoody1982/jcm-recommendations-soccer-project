export type ConfidenceLevel = 'STRONG' | 'MODERATE' | 'WEAK';

export type RecommendationType =
  | 'BTTS'
  | 'OVER_GOALS'
  | 'UNDER_GOALS'
  | 'BOOKING_POINTS'
  | 'VALUE_BET'
  | 'WINNING_FORM_MISMATCH'
  | 'LOSING_FORM_MISMATCH'
  | 'OVER_CORNERS'
  | 'UNDER_CORNERS'
  | 'CLEAN_SHEET'
  | 'FIRST_HALF_GOALS'
  | 'SECOND_HALF_GOALS'
  | 'MATCH_RESULT'
  | 'HOME_AWAY_SPECIALIST'
  | 'DRAW';

export interface Recommendation {
  fixtureId: number;
  homeTeamId: number;
  awayTeamId: number;
  homeTeamName: string;
  awayTeamName: string;
  matchDateUnix: number;
  leagueId: number | null;
  leagueName: string | null;
  leagueImage: string | null;
  type: RecommendationType;
  confidence: ConfidenceLevel;
  score: number;
  market: string;
  description: string;
  factors: Record<string, unknown>;
  generatedAt: string;
}

export interface RecommendationSummary {
  fixturesAnalyzed: number;
  totalRecommendations: number;
  strongRecommendations: number;
  moderateRecommendations: number;
  recommendationsByType: Record<RecommendationType, number>;
  generatedAt: string;
}
