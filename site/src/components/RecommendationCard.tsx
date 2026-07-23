import type { Recommendation } from '../types';
import styles from './RecommendationCard.module.css';

interface Props {
  recommendation: Recommendation;
}

const typeLabels: Record<string, string> = {
  BTTS: 'BTTS',
  OVER_GOALS: 'Over Goals',
  UNDER_GOALS: 'Under Goals',
  BOOKING_POINTS: 'Booking Points',
  VALUE_BET: 'Value Bet',
  WINNING_FORM_MISMATCH: 'Hot Streak',
  LOSING_FORM_MISMATCH: 'Cold Streak',
  OVER_CORNERS: 'Over Corners',
  UNDER_CORNERS: 'Under Corners',
  CLEAN_SHEET: 'Clean Sheet',
  FIRST_HALF_GOALS: '1H Goals',
  SECOND_HALF_GOALS: '2H Goals',
  MATCH_RESULT: 'Result',
  HOME_AWAY_SPECIALIST: 'Specialist',
  DRAW: 'Draw',
};

export function RecommendationCard({ recommendation }: Props) {
  const matchDate = new Date(recommendation.matchDateUnix * 1000);
  const formattedDate = matchDate.toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
  const formattedTime = matchDate.toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className={`${styles.card} ${styles[recommendation.confidence.toLowerCase()]}`}>
      <div className={styles.header}>
        <span className={styles.type}>{typeLabels[recommendation.type] || recommendation.type}</span>
        <span className={styles.confidence}>{recommendation.confidence}</span>
      </div>

      <div className={styles.match}>
        <span className={styles.team}>{recommendation.homeTeamName}</span>
        <span className={styles.vs}>vs</span>
        <span className={styles.team}>{recommendation.awayTeamName}</span>
      </div>

      <div className={styles.market}>{recommendation.market}</div>

      <div className={styles.score}>
        <div className={styles.scoreBar}>
          <div
            className={styles.scoreFill}
            style={{ width: `${Math.min(Number(recommendation.score) || 0, 100)}%` }}
          />
        </div>
        <span className={styles.scoreValue}>{Number(recommendation.score || 0).toFixed(1)}%</span>
      </div>

      <div className={styles.footer}>
        <span className={styles.date}>{formattedDate}</span>
        <span className={styles.time}>{formattedTime}</span>
      </div>
    </div>
  );
}
