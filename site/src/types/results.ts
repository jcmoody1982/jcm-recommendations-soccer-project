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
  /** Settled picks that carried a usable price; only these contribute to ROI. */
  pricedSample: number;
  avgOdds: number | null;
  /** Hit rate the average price demands before a bet turns a profit. */
  breakEvenRate: number | null;
  profitUnits: number | null;
  /** Profit per unit staked, as a percentage. Null when nothing was priced. */
  roi: number | null;
}

/** Published score band against what actually happened. Negative gap means overclaiming. */
export interface CalibrationBand {
  band: string;
  sampleSize: number;
  avgScore: number | null;
  hitRate: number | null;
  gap: number | null;
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
  /** Empty for engines scored in counts (corners, cards) rather than percentages. */
  calibration: CalibrationBand[];
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
