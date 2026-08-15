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

function headerClassName(showPrice: boolean, showPositionGap: boolean): string {
  if (showPrice && showPositionGap) return styles.tableHeaderPriceGap;
  if (showPositionGap) return styles.tableHeaderGap;
  if (showPrice) return styles.tableHeader;
  return styles.tableHeaderNoPrice;
}

function listClassName(showPrice: boolean, showPositionGap: boolean): string {
  if (showPrice && showPositionGap) return styles.listPriceGap;
  if (showPositionGap) return styles.listGap;
  if (showPrice) return styles.list;
  return styles.listNoPrice;
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
    showPositionGap: false,
  };

  const showPrice = config.showPrice;
  const showPositionGap = Boolean(config.showPositionGap);

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

  return (
    <section className={styles.section} id={sectionDomId(type)}>
      <h2 className={styles.header}>
        <span className={styles.icon}>{config.icon}</span>
        <span className={styles.title}>{config.title}</span>
        <span className={styles.count}>
          {visibleRecommendations.length} of {recommendations.length} picks
        </span>
      </h2>
      <div className={headerClassName(showPrice, showPositionGap)}>
        <span></span>
        <span>League</span>
        <span>Date / Time</span>
        <span>Fixture</span>
        <span>Selection</span>
        {showPositionGap && <span>Gap</span>}
        {showPrice && <span>Price</span>}
        <span>{config.scoreLabel}</span>
        <span></span>
      </div>
      <div className={listClassName(showPrice, showPositionGap)}>
        {visibleRecommendations.map((rec) => (
          <RecommendationRow
            key={`${rec.fixtureId}-${rec.type}`}
            recommendation={rec}
            showPrice={showPrice}
            showPositionGap={showPositionGap}
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
