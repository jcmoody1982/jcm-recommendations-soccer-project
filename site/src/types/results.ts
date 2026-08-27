export type PickOutcome = 'PENDING' | 'WIN' | 'LOSS' | 'VOID' | 'UNSUPPORTED';

export interface ResultsDaySummary {
  wins: number;
  losses: number;
  voids: number;
  pending: number;
  unsupported: number;
  hitRate: number | null;
}

export interface ResultsPick {
  id: number;
  type: string;
  market: string;
  confidence: string;
  score: number | null;
  odds: number | null;
  outcome: PickOutcome;
  description: string | null;
  eliteRank?: number | null;
}

export interface ResultsScoreline {
  home: number;
  away: number;
}

export interface ResultsFixture {
  fixtureId: number;
  homeTeamName: string;
  awayTeamName: string;
  matchDateUnix: number | null;
  leagueName: string | null;
  leagueImage: string | null;
  scoreline: ResultsScoreline | null;
  matchStatus: string | null;
  picks: ResultsPick[];
}

export interface DayResults {
  snapshotDate: string | null;
  summary: ResultsDaySummary;
  strongSummary: ResultsDaySummary;
  moderateSummary: ResultsDaySummary;
  eliteSummary: ResultsDaySummary;
  fixtures: ResultsFixture[];
  eliteFixtures: ResultsFixture[];
}

export type PerformancePeriod = '7d' | '30d' | '90d' | 'all';

export interface PerformanceBucket {
  wins: number;
  losses: number;
  voids: number;
  pending: number;
  unsupported: number;
  hitRate: number | null;
  sampleSize: number;
  enoughData: boolean;
}

export interface TypePerformance {
  type: string;
  overall: PerformanceBucket;
  byConfidence: {
    ELITE?: PerformanceBucket;
    STRONG: PerformanceBucket;
    MODERATE: PerformanceBucket;
  };
}

export interface ResultsPerformance {
  period: PerformancePeriod;
  fromDate: string | null;
  toDate: string;
  minSample: number;
  overall: PerformanceBucket;
  elite?: PerformanceBucket;
  byConfidence: {
    STRONG: PerformanceBucket;
    MODERATE: PerformanceBucket;
  };
  byType: TypePerformance[];
}
