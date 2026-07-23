import type { Recommendation, RecommendationType } from '../types';
import { RecommendationRow } from './RecommendationRow';
import styles from './RecommendationSection.module.css';

interface Props {
  type: RecommendationType;
  recommendations: Recommendation[];
  maxItems?: number;
}

const SECTION_CONFIG: Record<RecommendationType, { title: string; icon: string }> = {
  BTTS: { title: 'Both Teams To Score', icon: '⚽' },
  OVER_GOALS: { title: 'Over Goals', icon: '🎯' },
  UNDER_GOALS: { title: 'Under Goals', icon: '🛡️' },
  BOOKING_POINTS: { title: 'Booking Points', icon: '🟨' },
  VALUE_BET: { title: 'Value Bets', icon: '💰' },
  WINNING_FORM_MISMATCH: { title: 'Winning Form Mismatch', icon: '🔥' },
  LOSING_FORM_MISMATCH: { title: 'Losing Form Mismatch', icon: '📉' },
  OVER_CORNERS: { title: 'Over Corners', icon: '📐' },
  UNDER_CORNERS: { title: 'Under Corners', icon: '📏' },
  CLEAN_SHEET: { title: 'Clean Sheet', icon: '🧤' },
  FIRST_HALF_GOALS: { title: 'First Half Goals', icon: '1️⃣' },
  SECOND_HALF_GOALS: { title: 'Second Half Goals', icon: '2️⃣' },
  MATCH_RESULT: { title: 'Match Result', icon: '🏆' },
  HOME_AWAY_SPECIALIST: { title: 'Home/Away Specialist', icon: '🏟️' },
  DRAW: { title: 'Draw', icon: '🤝' },
};

export function RecommendationSection({ type, recommendations, maxItems = 5 }: Props) {
  const config = SECTION_CONFIG[type] || { title: type, icon: '📊' };
  const topRecommendations = recommendations
    .sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
    .slice(0, maxItems);

  if (topRecommendations.length === 0) {
    return null;
  }

  return (
    <section className={styles.section}>
      <h2 className={styles.header}>
        <span className={styles.icon}>{config.icon}</span>
        <span className={styles.title}>{config.title}</span>
        <span className={styles.count}>{topRecommendations.length} picks</span>
      </h2>
      <div className={styles.list}>
        {topRecommendations.map((rec) => (
          <RecommendationRow key={`${rec.fixtureId}-${rec.type}`} recommendation={rec} />
        ))}
      </div>
    </section>
  );
}
