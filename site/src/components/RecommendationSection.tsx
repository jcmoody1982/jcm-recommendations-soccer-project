import type { Recommendation, RecommendationType } from '../types';
import { RecommendationRow } from './RecommendationRow';
import styles from './RecommendationSection.module.css';

interface Props {
  type: RecommendationType;
  recommendations: Recommendation[];
  maxItems?: number;
}

const SECTION_CONFIG: Record<RecommendationType, { title: string; icon: string; scoreLabel: string }> = {
  BTTS: { title: 'Both Teams To Score', icon: '⚽', scoreLabel: 'Score' },
  OVER_GOALS: { title: 'Over Goals', icon: '🎯', scoreLabel: 'Score' },
  UNDER_GOALS: { title: 'Under Goals', icon: '🛡️', scoreLabel: 'Score' },
  BOOKING_POINTS: { title: 'Booking Points', icon: '🟨', scoreLabel: 'Predicted Points' },
  VALUE_BET: { title: 'Value Bets', icon: '💰', scoreLabel: 'Score' },
  WINNING_FORM_MISMATCH: { title: 'Winning Form Mismatch', icon: '🔥', scoreLabel: 'Score' },
  LOSING_FORM_MISMATCH: { title: 'Losing Form Mismatch', icon: '📉', scoreLabel: 'Score' },
  OVER_CORNERS: { title: 'Over Corners', icon: '📐', scoreLabel: 'Predicted Corners' },
  UNDER_CORNERS: { title: 'Under Corners', icon: '📏', scoreLabel: 'Predicted Corners' },
  CLEAN_SHEET: { title: 'Clean Sheet', icon: '🧤', scoreLabel: 'Score' },
  FIRST_HALF_GOALS: { title: 'First Half Goals', icon: '1️⃣', scoreLabel: 'Score' },
  SECOND_HALF_GOALS: { title: 'Second Half Goals', icon: '2️⃣', scoreLabel: 'Score' },
  MATCH_RESULT: { title: 'Match Result', icon: '🏆', scoreLabel: 'Score' },
  HOME_AWAY_SPECIALIST: { title: 'Home/Away Specialist', icon: '🏟️', scoreLabel: 'Score' },
  DRAW: { title: 'Draw', icon: '🤝', scoreLabel: 'Score' },
};

export function RecommendationSection({ type, recommendations, maxItems = 5 }: Props) {
  const config = SECTION_CONFIG[type] || { title: type, icon: '📊', scoreLabel: 'Score' };
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
      <div className={styles.tableHeader}>
        <span></span>
        <span>League</span>
        <span>Date / Time</span>
        <span>Fixture</span>
        <span>Selection</span>
        <span>Price</span>
        <span>{config.scoreLabel}</span>
      </div>
      <div className={styles.list}>
        {topRecommendations.map((rec) => (
          <RecommendationRow key={`${rec.fixtureId}-${rec.type}`} recommendation={rec} />
        ))}
      </div>
    </section>
  );
}
