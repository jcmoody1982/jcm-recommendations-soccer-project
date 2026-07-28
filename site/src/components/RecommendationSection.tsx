import { useState } from 'react';
import type { Recommendation, RecommendationType } from '../types';
import { RecommendationRow } from './RecommendationRow';
import { RecommendationIcons, ChartIcon } from './Icons';
import styles from './RecommendationSection.module.css';

interface Props {
  type: RecommendationType;
  recommendations: Recommendation[];
  initialItems?: number;
  incrementBy?: number;
}

interface SectionConfig {
  title: string;
  scoreLabel: string;
  showPrice: boolean;
}

const SECTION_CONFIG: Record<RecommendationType, SectionConfig> = {
  BTTS: { title: 'Both Teams To Score', scoreLabel: 'Score', showPrice: true },
  OVER_GOALS: { title: 'Over Goals', scoreLabel: 'Score', showPrice: true },
  UNDER_GOALS: { title: 'Under Goals', scoreLabel: 'Score', showPrice: true },
  BOOKING_POINTS: { title: 'Booking Points', scoreLabel: 'Predicted Points', showPrice: false },
  VALUE_BET: { title: 'Value Bets', scoreLabel: 'Score', showPrice: true },
  WINNING_FORM_MISMATCH: { title: 'Winning Form Mismatch', scoreLabel: 'Form Divergence', showPrice: false },
  LOSING_FORM_MISMATCH: { title: 'Losing Form Mismatch', scoreLabel: 'Form Divergence', showPrice: false },
  OVER_CORNERS: { title: 'Over Corners', scoreLabel: 'Predicted Corners', showPrice: false },
  UNDER_CORNERS: { title: 'Under Corners', scoreLabel: 'Predicted Corners', showPrice: false },
  CLEAN_SHEET: { title: 'Clean Sheet', scoreLabel: 'Score', showPrice: false },
  FIRST_HALF_GOALS: { title: 'First Half Goals', scoreLabel: 'Confidence', showPrice: false },
  SECOND_HALF_GOALS: { title: 'Second Half Goals', scoreLabel: 'Score', showPrice: false },
  MATCH_RESULT: { title: 'Match Result', scoreLabel: 'Score', showPrice: true },
  HOME_AWAY_SPECIALIST: { title: 'Home/Away Specialist', scoreLabel: 'Disparity Index', showPrice: false },
  DRAW: { title: 'Draw', scoreLabel: 'Score', showPrice: true },
  DOUBLE_CHANCE: { title: 'Double Chance', scoreLabel: 'Probability', showPrice: false },
  RESULT_BTTS: { title: 'Result + BTTS', scoreLabel: 'Combined', showPrice: false },
  TOP_VS_BOTTOM: { title: 'Top vs Bottom', scoreLabel: 'Quality Score', showPrice: false },
};

export function RecommendationSection({ 
  type, 
  recommendations, 
  initialItems = 5,
  incrementBy = 5 
}: Props) {
  const [visibleCount, setVisibleCount] = useState(initialItems);
  
  const config = SECTION_CONFIG[type] || { title: type, scoreLabel: 'Score', showPrice: true };
  const IconComponent = RecommendationIcons[type] || ChartIcon;
  
  const visibleRecommendations = recommendations.slice(0, visibleCount);
  const hasMore = recommendations.length > visibleCount;
  const canShowLess = visibleCount > initialItems;
  const remainingCount = recommendations.length - visibleCount;

  if (recommendations.length === 0) {
    return null;
  }

  const handleShowMore = () => {
    setVisibleCount(prev => prev + incrementBy);
  };

  const handleShowLess = () => {
    setVisibleCount(prev => Math.max(initialItems, prev - incrementBy));
  };

  const headerClass = config.showPrice ? styles.tableHeader : styles.tableHeaderNoPrice;
  const listClass = config.showPrice ? styles.list : styles.listNoPrice;

  return (
    <section className={styles.section}>
      <h2 className={styles.header}>
        <span className={styles.icon}><IconComponent size={22} /></span>
        <span className={styles.title}>{config.title}</span>
        <span className={styles.count}>
          {visibleRecommendations.length} of {recommendations.length} picks
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
        <span></span>
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
      {(hasMore || canShowLess) && (
        <div className={styles.buttonGroup}>
          {canShowLess && (
            <button className={styles.showLessButton} onClick={handleShowLess}>
              Show Less
            </button>
          )}
          {hasMore && (
            <button className={styles.showMoreButton} onClick={handleShowMore}>
              Show {Math.min(incrementBy, remainingCount)} More
            </button>
          )}
        </div>
      )}
    </section>
  );
}
