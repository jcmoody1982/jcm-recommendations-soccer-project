import { useState } from 'react';
import type { Recommendation, RecommendationType } from '../types';
import { RecommendationRow } from './RecommendationRow';
import { SECTION_CONFIG, sectionDomId } from '../utils/recommendationSections';
import styles from './RecommendationSection.module.css';

interface Props {
  type: RecommendationType;
  recommendations: Recommendation[];
  initialItems?: number;
  incrementBy?: number;
}

export function RecommendationSection({
  type,
  recommendations,
  initialItems = 5,
  incrementBy = 5,
}: Props) {
  const [visibleCount, setVisibleCount] = useState(initialItems);

  const config = SECTION_CONFIG[type] || {
    title: type,
    icon: '📊',
    scoreLabel: 'Score',
    scoreUnit: '',
    showPrice: true,
  };

  const visibleRecommendations = recommendations.slice(0, visibleCount);
  const hasMore = recommendations.length > visibleCount;
  const canShowLess = visibleCount > initialItems;
  const remainingCount = recommendations.length - visibleCount;

  if (recommendations.length === 0) {
    return null;
  }

  const handleShowMore = () => {
    setVisibleCount((prev) => prev + incrementBy);
  };

  const handleShowLess = () => {
    setVisibleCount((prev) => Math.max(initialItems, prev - incrementBy));
  };

  const headerClass = config.showPrice ? styles.tableHeader : styles.tableHeaderNoPrice;
  const listClass = config.showPrice ? styles.list : styles.listNoPrice;

  return (
    <section className={styles.section} id={sectionDomId(type)}>
      <h2 className={styles.header}>
        <span className={styles.icon}>{config.icon}</span>
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
