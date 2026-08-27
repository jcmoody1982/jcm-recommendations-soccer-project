import type { RecommendationType } from '../types';

export interface SectionConfig {
  title: string;
  /** Column header for the numeric score */
  scoreLabel: string;
  /** Unit suffix shown next to the score value */
  scoreUnit: string;
  showPrice: boolean;
  /** Show league position gap after Selection (Top vs Bottom). */
  showPositionGap?: boolean;
}

export const SECTION_ORDER: RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'OVER_15_GOALS',
  'OVER_25_GOALS',
  'PLAYER_TO_SCORE',
  'PLAYER_TO_ASSIST',
  'DRAW',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
  'OVER_GOALS',
  'UNDER_GOALS',
  'BOOKING_POINTS',
  'OVER_CORNERS',
  'UNDER_CORNERS',
  'HOME_AWAY_SPECIALIST',
  'WINNING_FORM_MISMATCH',
  'LOSING_FORM_MISMATCH',
];

export const SECTION_CONFIG: Record<RecommendationType, SectionConfig> = {
  MATCH_RESULT: {
    title: 'Match Result',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  BTTS: {
    title: 'Both Teams To Score',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  DOUBLE_CHANCE: {
    title: 'Double Chance',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  RESULT_BTTS: {
    title: 'Result + BTTS',
    scoreLabel: 'Combined',
    scoreUnit: '%',
    showPrice: false,
  },
  TOP_VS_BOTTOM: {
    title: 'Top vs Bottom',
    scoreLabel: 'Quality',
    scoreUnit: '%',
    showPrice: false,
    showPositionGap: true,
  },
  OVER_15_GOALS: {
    title: 'Over 1.5 Goals',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  OVER_25_GOALS: {
    title: 'Over 2.5 Goals',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  PLAYER_TO_SCORE: {
    title: 'Player to Score',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  PLAYER_TO_ASSIST: {
    title: 'Player to Assist',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  DRAW: {
    title: 'Draw',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  FIRST_HALF_GOALS: {
    title: 'First Half Goals',
    scoreLabel: 'Confidence',
    scoreUnit: '%',
    showPrice: false,
  },
  SECOND_HALF_GOALS: {
    title: 'Second Half Goals',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  VALUE_BET: {
    title: 'Value Bets',
    scoreLabel: 'Value score',
    scoreUnit: '%',
    showPrice: true,
  },
  OVER_GOALS: {
    title: 'Over Goals',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  UNDER_GOALS: {
    title: 'Under Goals',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  CLEAN_SHEET: {
    title: 'Clean Sheet',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  BOOKING_POINTS: {
    title: 'Booking Points',
    scoreLabel: 'Predicted pts',
    scoreUnit: ' pts',
    showPrice: false,
  },
  OVER_CORNERS: {
    title: 'Over Corners',
    scoreLabel: 'Pred. corners',
    scoreUnit: '',
    showPrice: false,
  },
  UNDER_CORNERS: {
    title: 'Under Corners',
    scoreLabel: 'Pred. corners',
    scoreUnit: '',
    showPrice: false,
  },
  HOME_AWAY_SPECIALIST: {
    title: 'Home/Away Specialist',
    scoreLabel: 'Disparity',
    scoreUnit: ' pts',
    showPrice: false,
  },
  WINNING_FORM_MISMATCH: {
    title: 'Winning Form Mismatch',
    scoreLabel: 'Form gap',
    scoreUnit: ' pts',
    showPrice: false,
  },
  LOSING_FORM_MISMATCH: {
    title: 'Losing Form Mismatch',
    scoreLabel: 'Form gap',
    scoreUnit: ' pts',
    showPrice: false,
  },
};

export function sectionTitle(type: RecommendationType | string): string {
  return SECTION_CONFIG[type as RecommendationType]?.title ?? String(type).replaceAll('_', ' ');
}

export function sectionDomId(type: RecommendationType): string {
  return `rec-section-${type}`;
}
