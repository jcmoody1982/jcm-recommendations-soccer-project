import { useState } from 'react';
import type { Recommendation, RecommendationType } from '../types';
import { RecommendationRow } from './RecommendationRow';
import styles from './RecommendationSection.module.css';

interface Props {
  type: RecommendationType;
  recommendations: Recommendation[];
  initialItems?: number;
  incrementBy?: number;
}

interface SectionConfig {
  title: string;
  icon: string;
  scoreLabel: string;
  showPrice: boolean;
}

const SECTION_CONFIG: Record<RecommendationType, SectionConfig> = {
  BTTS: { title: 'Both Teams To Score', icon: '⚽', scoreLabel: 'Score', showPrice: true },
  OVER_GOALS: { title: 'Over Goals', icon: '🎯', scoreLabel: 'Score', showPrice: true },
  UNDER_GOALS: { title: 'Under Goals', icon: '🛡️', scoreLabel: 'Score', showPrice: true },
  BOOKING_POINTS: { title: 'Booking Points', icon: '🟨', scoreLabel: 'Predicted Points', showPrice: false },
  VALUE_BET: { title: 'Value Bets', icon: '💰', scoreLabel: 'Score', showPrice: true },
  WINNING_FORM_MISMATCH: { title: 'Winning Form Mismatch', icon: '🔥', scoreLabel: 'Form Divergence', showPrice: false },
  LOSING_FORM_MISMATCH: { title: 'Losing Form Mismatch', icon: '📉', scoreLabel: 'Form Divergence', showPrice: false },
  OVER_CORNERS: { title: 'Over Corners', icon: '📐', scoreLabel: 'Predicted Corners', showPrice: false },
  UNDER_CORNERS: { title: 'Under Corners', icon: '📏', scoreLabel: 'Predicted Corners', showPrice: false },
  CLEAN_SHEET: { title: 'Clean Sheet', icon: '🧤', scoreLabel: 'Score', showPrice: false },
  FIRST_HALF_GOALS: { title: 'First Half Goals', icon: '1️⃣', scoreLabel: 'Confidence', showPrice: false },
  SECOND_HALF_GOALS: { title: 'Second Half Goals', icon: '2️⃣', scoreLabel: 'Score', showPrice: false },
  MATCH_RESULT: { title: 'Match Result', icon: '🏆', scoreLabel: 'Score', showPrice: true },
  HOME_AWAY_SPECIALIST: { title: 'Home/Away Specialist', icon: '🏟️', scoreLabel: 'Disparity Index', showPrice: false },
  DRAW: { title: 'Draw', icon: '🤝', scoreLabel: 'Score', showPrice: true },
};

export function RecommendationSection({ 
  type, 
  recommendations, 
  initialItems = 5,
  incrementBy = 5 
}: Props) {
  const [visibleCount, setVisibleCount] = useState(initialItems);
  
  const config = SECTION_CONFIG[type] || { title: type, icon: '📊', scoreLabel: 'Score', showPrice: true };
  
  const sortedRecommendations = [...recommendations].sort(
    (a, b) => (Number(b.score) || 0) - (Number(a.score) || 0)
  );
  
  const visibleRecommendations = sortedRecommendations.slice(0, visibleCount);
  const hasMore = sortedRecommendations.length > visibleCount;
  const remainingCount = sortedRecommendations.length - visibleCount;

  if (sortedRecommendations.length === 0) {
    return null;
  }

  const handleShowMore = () => {
    setVisibleCount(prev => prev + incrementBy);
  };

  const headerClass = config.showPrice ? styles.tableHeader : styles.tableHeaderNoPrice;
  const listClass = config.showPrice ? styles.list : styles.listNoPrice;

  return (
    <section className={styles.section}>
      <h2 className={styles.header}>
        <span className={styles.icon}>{config.icon}</span>
        <span className={styles.title}>{config.title}</span>
        <span className={styles.count}>
          {visibleRecommendations.length} of {sortedRecommendations.length} picks
        </span>
      </h2>
      <div className={headerClass}>
        <span></span>
        <span>League</span>
        <span>Date / Time</span>
        <span>Fixture</span>
        <span>Selection</span>
        {config.showPrice && <span>Price</span>}
        <span>{config.scoreLabel}</span>
      </div>
      <div className={listClass}>
        {visibleRecommendations.map((rec) => (
          <RecommendationRow 
            key={`${rec.fixtureId}-${rec.type}`} 
            recommendation={rec} 
            showPrice={config.showPrice}
          />
        ))}
      </div>
      {hasMore && (
        <button className={styles.showMoreButton} onClick={handleShowMore}>
          Show {Math.min(incrementBy, remainingCount)} More
        </button>
      )}
    </section>
  );
}
