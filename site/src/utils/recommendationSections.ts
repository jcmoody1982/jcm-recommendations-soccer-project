import type { RecommendationType } from '../types';

export interface SectionConfig {
  title: string;
  icon: string;
  /** Column header for the numeric score */
  scoreLabel: string;
  /** Unit suffix shown next to the score value */
  scoreUnit: string;
  showPrice: boolean;
}

export const SECTION_ORDER: RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'DRAW',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
  'OVER_GOALS',
  'UNDER_GOALS',
  'CLEAN_SHEET',
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
    icon: '🏆',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  BTTS: {
    title: 'Both Teams To Score',
    icon: '⚽',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  DOUBLE_CHANCE: {
    title: 'Double Chance',
    icon: '🎲',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  RESULT_BTTS: {
    title: 'Result + BTTS',
    icon: '🎯⚽',
    scoreLabel: 'Combined',
    scoreUnit: '%',
    showPrice: false,
  },
  TOP_VS_BOTTOM: {
    title: 'Top vs Bottom',
    icon: '⬆️⬇️',
    scoreLabel: 'Quality',
    scoreUnit: '%',
    showPrice: false,
  },
  DRAW: {
    title: 'Draw',
    icon: '🤝',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  FIRST_HALF_GOALS: {
    title: 'First Half Goals',
    icon: '1️⃣',
    scoreLabel: 'Confidence',
    scoreUnit: '%',
    showPrice: false,
  },
  SECOND_HALF_GOALS: {
    title: 'Second Half Goals',
    icon: '2️⃣',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  VALUE_BET: {
    title: 'Value Bets',
    icon: '💰',
    scoreLabel: 'Value score',
    scoreUnit: '%',
    showPrice: true,
  },
  OVER_GOALS: {
    title: 'Over Goals',
    icon: '🎯',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  UNDER_GOALS: {
    title: 'Under Goals',
    icon: '🛡️',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: true,
  },
  CLEAN_SHEET: {
    title: 'Clean Sheet',
    icon: '🧤',
    scoreLabel: 'Probability',
    scoreUnit: '%',
    showPrice: false,
  },
  BOOKING_POINTS: {
    title: 'Booking Points',
    icon: '🟨',
    scoreLabel: 'Predicted pts',
    scoreUnit: ' pts',
    showPrice: false,
  },
  OVER_CORNERS: {
    title: 'Over Corners',
    icon: '📐',
    scoreLabel: 'Pred. corners',
    scoreUnit: '',
    showPrice: false,
  },
  UNDER_CORNERS: {
    title: 'Under Corners',
    icon: '📏',
    scoreLabel: 'Pred. corners',
    scoreUnit: '',
    showPrice: false,
  },
  HOME_AWAY_SPECIALIST: {
    title: 'Home/Away Specialist',
    icon: '🏟️',
    scoreLabel: 'Disparity',
    scoreUnit: ' pts',
    showPrice: false,
  },
  WINNING_FORM_MISMATCH: {
    title: 'Winning Form Mismatch',
    icon: '🔥',
    scoreLabel: 'Form gap',
    scoreUnit: ' pts',
    showPrice: false,
  },
  LOSING_FORM_MISMATCH: {
    title: 'Losing Form Mismatch',
    icon: '📉',
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
