import type { Recommendation } from '../types';
import { RecommendationRow } from './RecommendationRow';
import { sectionTitle } from '../utils/recommendationSections';
import styles from './RecommendationSection.module.css';

interface Props {
  recommendations: Recommendation[];
}

/**
 * Cross-market Elite Picks board (UC-036). Always shows price when present;
 * score column is generic because types are mixed.
 */
export function ElitePicksSection({ recommendations }: Props) {
  if (recommendations.length === 0) {
    return null;
  }

  return (
    <section className={styles.section} id="rec-section-elite-picks">
      <h2 className={styles.header}>
        <span className={styles.icon}>👑</span>
        <span className={styles.title}>Elite Picks</span>
        <span className={styles.count}>
          {recommendations.length} pick{recommendations.length !== 1 ? 's' : ''}
        </span>
      </h2>
      <p className={styles.eliteBlurb}>
        Top Strong selections across markets (one per fixture)
      </p>
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
        {recommendations.map((rec) => (
          <div key={`${rec.fixtureId}-${rec.type}-elite`} className={styles.eliteItem}>
            <span className={styles.eliteType}>{sectionTitle(rec.type)}</span>
            <RecommendationRow
              recommendation={rec}
              showPrice
              showPositionGap={false}
            />
          </div>
        ))}
      </div>
    </section>
  );
}
