import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { RecommendationSection } from '../components/RecommendationSection';
import type { RecommendationType } from '../types';
import styles from './Recommendations.module.css';

const SECTION_ORDER: RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'DRAW',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
  'OVER_GOALS',
  'UNDER_GOALS',
  'CLEAN_SHEET',
  'BOOKING_POINTS',
  'OVER_CORNERS',
  'UNDER_CORNERS',
  'HOME_AWAY_SPECIALIST',
  'WINNING_FORM_MISMATCH',
  'LOSING_FORM_MISMATCH',
];

export default function Recommendations() {
  const [daysAhead, setDaysAhead] = useState(7);

  const { data: groupedRecommendations, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['recommendations-grouped', daysAhead],
    queryFn: () => recommendationService.getGrouped(daysAhead),
  });

  const totalCount = groupedRecommendations
    ? Object.values(groupedRecommendations).reduce((sum, recs) => sum + recs.length, 0)
    : 0;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Recommendations</h1>
        <div className={styles.headerRight}>
          <label className={styles.filterLabel}>Filter</label>
          <select
            value={daysAhead}
            onChange={(e) => setDaysAhead(Number(e.target.value))}
            className={styles.select}
          >
            <option value={0.5}>Next 12 hours</option>
            <option value={1}>Next 24 hours</option>
            <option value={3}>Next 3 days</option>
            <option value={7}>Next 7 days</option>
          </select>
        </div>
      </header>

      {isLoading ? (
        <div className={styles.loading}>Loading recommendations...</div>
      ) : isError ? (
        <div className={styles.error}>
          <div className={styles.errorIcon}>⚠️</div>
          <h2 className={styles.errorTitle}>Failed to load recommendations</h2>
          <p className={styles.errorMessage}>
            {error instanceof Error ? error.message : 'An unexpected error occurred. Please try again.'}
          </p>
          <button className={styles.retryButton} onClick={() => refetch()}>
            Try Again
          </button>
        </div>
      ) : groupedRecommendations && totalCount > 0 ? (
        <div className={styles.sections}>
          {SECTION_ORDER.map((type) => {
            const recommendations = groupedRecommendations[type] || [];
            return (
              <RecommendationSection
                key={type}
                type={type}
                recommendations={recommendations}
                initialItems={5}
              />
            );
          })}
        </div>
      ) : (
        <p className={styles.empty}>No recommendations available for this time period.</p>
      )}

      <footer className={styles.footer}>
        <span className={styles.totalCount}>{totalCount} total picks</span>
      </footer>
    </div>
  );
}
