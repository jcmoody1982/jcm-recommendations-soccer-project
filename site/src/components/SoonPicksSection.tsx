import { useEffect, useState } from 'react';
import type { Recommendation } from '../types';
import { elitePickKey } from '../utils/elitePicks';
import { sectionTitle } from '../utils/recommendationSections';
import { MarketIcon } from './MarketIcon';
import { RecommendationRow } from './RecommendationRow';
import styles from './RecommendationSection.module.css';

interface Props {
  recommendations: Recommendation[];
  eliteKeys?: Set<string>;
  onShowOnly: () => void;
  initialItems?: number;
  incrementBy?: number;
}

/**
 * Cross-market board of picks kicking off within three hours.
 * Shown only on All kickoffs; the header action narrows the page to that window.
 */
export function SoonPicksSection({
  recommendations,
  eliteKeys,
  onShowOnly,
  initialItems = 5,
  incrementBy = 5,
}: Props) {
  const [visibleCount, setVisibleCount] = useState(initialItems);
  const listKey = recommendations.map((rec) => `${rec.fixtureId}-${rec.type}`).join('|');

  useEffect(() => {
    setVisibleCount(initialItems);
  }, [listKey, initialItems]);

  if (recommendations.length === 0) {
    return null;
  }

  const visibleRecommendations = recommendations.slice(0, visibleCount);
  const hasMore = recommendations.length > visibleCount;
  const canShowLess = visibleCount > initialItems && recommendations.length > initialItems;
  const remainingCount = recommendations.length - visibleCount;

  return (
    <section className={styles.section} id="rec-section-starting-soon">
      <h2 className={styles.header}>
        <MarketIcon type="SOON" title="Starting soon" />
        <span className={styles.title}>Starting soon</span>
        <span className={styles.headerAside}>
          <button type="button" className={styles.showOnly} onClick={onShowOnly}>
            Show only these
          </button>
          <span className={styles.count}>
            {visibleRecommendations.length} of {recommendations.length} pick
            {recommendations.length !== 1 ? 's' : ''}
          </span>
        </span>
      </h2>
      <p className={styles.eliteBlurb}>Kicking off within 3 hours</p>
      <div className={styles.tableHeader}>
        <span></span>
        <span>League</span>
        <span>Date / Time</span>
        <span>Fixture</span>
        <span>Selection</span>
        <span>Price</span>
        <span>Score</span>
        <span></span>
      </div>
      <div className={styles.list}>
        {visibleRecommendations.map((rec) => (
          <div key={`${rec.fixtureId}-${rec.type}-soon`} className={styles.eliteItem}>
            <span className={styles.eliteType}>{sectionTitle(rec.type)}</span>
            <RecommendationRow
              recommendation={rec}
              showPrice
              showPositionGap={false}
              isElite={eliteKeys?.has(elitePickKey(rec.fixtureId, rec.type))}
            />
          </div>
        ))}
      </div>
      {(hasMore || canShowLess) && (
        <div className={styles.buttonGroup}>
          {canShowLess && (
            <button
              type="button"
              className={styles.showLessButton}
              onClick={() => setVisibleCount((prev) => Math.max(initialItems, prev - incrementBy))}
            >
              Show Less
            </button>
          )}
          {hasMore && (
            <button
              type="button"
              className={styles.showMoreButton}
              onClick={() => setVisibleCount((prev) => prev + incrementBy)}
            >
              Show {Math.min(incrementBy, remainingCount)} More
            </button>
          )}
        </div>
      )}
    </section>
  );
}
