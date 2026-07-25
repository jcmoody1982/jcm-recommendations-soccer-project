import type { Recommendation } from '../types';
import { useShortlist } from '../contexts/ShortlistContext';
import styles from './RecommendationRow.module.css';

interface Props {
  recommendation: Recommendation;
  showPrice?: boolean;
}

const ConfidenceIcon = ({ level }: { level: string }) => {
  if (level === 'STRONG') {
    return (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" fill="#22c55e" stroke="#16a34a" strokeWidth="1"/>
        <path d="M6 10L9 13L14 7" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    );
  }
  if (level === 'MODERATE') {
    return (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" fill="#f59e0b" stroke="#d97706" strokeWidth="1"/>
        <path d="M10 6V10" stroke="white" strokeWidth="2" strokeLinecap="round"/>
        <circle cx="10" cy="13" r="1" fill="white"/>
      </svg>
    );
  }
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle cx="10" cy="10" r="9" fill="#9ca3af" stroke="#6b7280" strokeWidth="1"/>
      <path d="M7 7L13 13M13 7L7 13" stroke="white" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
};

export function RecommendationRow({ recommendation, showPrice = true }: Props) {
  const { isShortlisted, toggleShortlist } = useShortlist();
  const isInShortlist = isShortlisted(recommendation.fixtureId, recommendation.type);

  const handleToggleShortlist = (e: React.MouseEvent) => {
    e.stopPropagation();
    toggleShortlist(recommendation.fixtureId, recommendation.type);
  };
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

  const rowClass = showPrice ? styles.row : styles.rowNoPrice;

  return (
    <>
      {/* Desktop row layout */}
      <div className={rowClass}>
        <span className={styles.sentiment} title={recommendation.confidence}>
          <ConfidenceIcon level={recommendation.confidence} />
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
        {showPrice && (
          <span className={styles.price}>
            {recommendation.odds ? recommendation.odds.toFixed(2) : '-'}
          </span>
        )}
        <span className={styles.score}>{score}{scoreUnit}</span>
        <button 
          className={`${styles.starButton} ${isInShortlist ? styles.starred : ''}`}
          onClick={handleToggleShortlist}
          title={isInShortlist ? 'Remove from shortlist' : 'Add to shortlist'}
        >
          {isInShortlist ? '★' : '☆'}
        </button>
      </div>

      {/* Mobile card layout */}
      <div className={styles.mobileCard}>
        <div className={styles.mobileCardHeader}>
          <span className={styles.mobileSentiment}>
            <ConfidenceIcon level={recommendation.confidence} />
          </span>
          {recommendation.leagueImage ? (
            <img 
              src={recommendation.leagueImage} 
              alt={recommendation.leagueName || 'League'} 
              className={styles.mobileLeagueIcon}
            />
          ) : (
            <span className={styles.mobileLeaguePlaceholder}>⚽</span>
          )}
          <span className={styles.mobileFixture}>
            {recommendation.homeTeamName} vs {recommendation.awayTeamName}
          </span>
          <button 
            className={`${styles.mobileStarButton} ${isInShortlist ? styles.starred : ''}`}
            onClick={handleToggleShortlist}
            title={isInShortlist ? 'Remove from shortlist' : 'Add to shortlist'}
          >
            {isInShortlist ? '★' : '☆'}
          </button>
        </div>
        <div className={styles.mobileCardBody}>
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>When</span>
            <span className={styles.mobileValue}>{formattedDate} {formattedTime}</span>
          </div>
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>Selection</span>
            <span className={styles.mobileValue}>{recommendation.market}</span>
          </div>
          {showPrice && recommendation.odds && (
            <div className={styles.mobileInfo}>
              <span className={styles.mobileLabel}>Price</span>
              <span className={styles.mobileValue}>{recommendation.odds.toFixed(2)}</span>
            </div>
          )}
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>Score</span>
            <span className={styles.mobileScore}>{score}{scoreUnit}</span>
          </div>
        </div>
      </div>
    </>
  );
}
