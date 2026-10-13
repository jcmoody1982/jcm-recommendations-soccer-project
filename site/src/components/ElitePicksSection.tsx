import { useState } from 'react';
import type { PerformanceBucket } from '../types';
import { MarketIcon } from './MarketIcon';
import { RecommendationRow } from './RecommendationRow';
import { sectionTitle } from '../utils/recommendationSections';
import { eliteDayLabel, type EliteDay } from '../utils/elitePicks';
import { formatTrackRecord } from '../utils/trackRecord';
import styles from './RecommendationSection.module.css';

/** Matchdays open by default; later days sit behind a button. */
const INITIAL_DAYS = 2;

interface Props {
  days: EliteDay[];
  trackRecord?: PerformanceBucket;
}

/**
 * Cross-market Elite Picks board (UC-036), ranked per matchday on the server so it matches the
 * Elite record on Results. Always shows price; score column is generic because types are mixed.
 */
export function ElitePicksSection({ days, trackRecord }: Props) {
  const [showAllDays, setShowAllDays] = useState(false);

  if (days.length === 0) {
    return null;
  }

  const total = days.reduce((sum, day) => sum + day.picks.length, 0);
  const visibleDays = showAllDays ? days : days.slice(0, INITIAL_DAYS);
  const hiddenDays = days.length - visibleDays.length;
  const trackRecordLine = formatTrackRecord(trackRecord);

  return (
    <section className={styles.section} id="rec-section-elite-picks">
      <h2 className={styles.header}>
        <MarketIcon type="ELITE" title="Elite Picks" />
        <span className={styles.title}>Elite Picks</span>
        <span className={styles.count}>
          {total} pick{total !== 1 ? 's' : ''}
        </span>
      </h2>
      {trackRecordLine && <p className={styles.trackRecord}>{trackRecordLine}</p>}
      <p className={styles.eliteBlurb}>
        Our top Strong picks for each matchday, up to 10 a day: one per fixture, at most 3 from any
        one market family, all priced 1.20 or longer
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
      {visibleDays.map((day) => (
        <div key={day.date}>
          <h3 className={styles.eliteDay}>{eliteDayLabel(day.date)}</h3>
          <div className={styles.list}>
            {day.picks.map((rec) => (
              <div key={`${rec.fixtureId}-${rec.type}-elite`} className={styles.eliteItem}>
                <span className={styles.eliteType}>{sectionTitle(rec.type)}</span>
                <RecommendationRow
                  recommendation={rec}
                  showPrice
                  showPositionGap={false}
                  isElite
                />
              </div>
            ))}
          </div>
        </div>
      ))}
      {(hiddenDays > 0 || showAllDays) && days.length > INITIAL_DAYS && (
        <div className={styles.buttonGroup}>
          {showAllDays ? (
            <button className={styles.showLessButton} onClick={() => setShowAllDays(false)}>
              Show Less
            </button>
          ) : (
            <button className={styles.showMoreButton} onClick={() => setShowAllDays(true)}>
              Show {hiddenDays} More Day{hiddenDays !== 1 ? 's' : ''}
            </button>
          )}
        </div>
      )}
    </section>
  );
}
