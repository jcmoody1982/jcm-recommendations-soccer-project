import type { Recommendation } from '../types';
import styles from './RecommendationRow.module.css';

interface Props {
  recommendation: Recommendation;
}

const CONFIDENCE_ICONS: Record<string, string> = {
  STRONG: '🟢',
  MODERATE: '🟡',
  WEAK: '🔴',
};

export function RecommendationRow({ recommendation }: Props) {
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

  const score = Number(recommendation.score || 0).toFixed(0);
  const isBookingPoints = recommendation.type === 'BOOKING_POINTS';
  const isCorners = recommendation.type === 'OVER_CORNERS' || recommendation.type === 'UNDER_CORNERS';
  const isFormMismatch = recommendation.type === 'WINNING_FORM_MISMATCH' || recommendation.type === 'LOSING_FORM_MISMATCH';
  const isHomeAwaySpecialist = recommendation.type === 'HOME_AWAY_SPECIALIST';
  const scoreUnit = isBookingPoints || isFormMismatch || isHomeAwaySpecialist ? ' pts' : isCorners ? '' : '%';
  const sentimentIcon = CONFIDENCE_ICONS[recommendation.confidence] || '⚪';

  return (
    <div className={styles.row}>
      <span className={styles.sentiment} title={recommendation.confidence}>
        {sentimentIcon}
      </span>
      <span className={styles.league}>
        {recommendation.leagueImage ? (
          <img 
            src={recommendation.leagueImage} 
            alt={recommendation.leagueName || 'League'} 
            className={styles.leagueIcon}
          />
        ) : (
          <span className={styles.leaguePlaceholder}>⚽</span>
        )}
      </span>
      <span className={styles.datetime}>
        <span className={styles.date}>{formattedDate}</span>
        <span className={styles.time}>{formattedTime}</span>
      </span>
      <span className={styles.fixture}>
        {recommendation.homeTeamName} vs {recommendation.awayTeamName}
      </span>
      <span className={styles.market}>{recommendation.market}</span>
      <span className={styles.price}>
        {recommendation.odds ? recommendation.odds.toFixed(2) : '-'}
      </span>
      <span className={styles.score}>{score}{scoreUnit}</span>
    </div>
  );
}
