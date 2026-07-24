import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { RecommendationSection } from '../components/RecommendationSection';
import type { RecommendationType } from '../types';
import styles from './Recommendations.module.css';

const SECTION_ORDER: RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
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
  'WINNING_FORM_MISMATCH',
  'LOSING_FORM_MISMATCH',
  'HOME_AWAY_SPECIALIST',
];

export default function Recommendations() {
  const [daysAhead, setDaysAhead] = useState(7);

  const { data: groupedRecommendations, isLoading } = useQuery({
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
          <span className={styles.totalCount}>{totalCount} total picks</span>
          <select
            value={daysAhead}
            onChange={(e) => setDaysAhead(Number(e.target.value))}
            className={styles.select}
          >
            <option value={1}>Next 24 hours</option>
            <option value={3}>Next 3 days</option>
            <option value={7}>Next 7 days</option>
            <option value={14}>Next 14 days</option>
          </select>
        </div>
      </header>

      {isLoading ? (
        <div className={styles.loading}>Loading recommendations...</div>
      ) : groupedRecommendations && totalCount > 0 ? (
        <div className={styles.sections}>
          {SECTION_ORDER.map((type) => {
            const recommendations = groupedRecommendations[type] || [];
            return (
              <RecommendationSection
                key={type}
                type={type}
                recommendations={recommendations}
                maxItems={5}
              />
            );
          })}
        </div>
      ) : (
        <p className={styles.empty}>No recommendations available for this time period.</p>
      )}
    </div>
  );
}
