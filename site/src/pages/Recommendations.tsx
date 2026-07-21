import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { RecommendationCard } from '../components/RecommendationCard';
import type { RecommendationType, ConfidenceLevel } from '../types';
import styles from './Recommendations.module.css';

const RECOMMENDATION_TYPES: { value: RecommendationType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'BTTS', label: 'BTTS' },
  { value: 'OVER_GOALS', label: 'Over Goals' },
  { value: 'UNDER_GOALS', label: 'Under Goals' },
  { value: 'BOOKING_POINTS', label: 'Booking Points' },
  { value: 'VALUE_BET', label: 'Value Bets' },
  { value: 'MATCH_RESULT', label: 'Match Result' },
  { value: 'CLEAN_SHEET', label: 'Clean Sheet' },
  { value: 'OVER_CORNERS', label: 'Over Corners' },
  { value: 'DRAW', label: 'Draw' },
];

const CONFIDENCE_LEVELS: { value: ConfidenceLevel | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'STRONG', label: 'Strong' },
  { value: 'MODERATE', label: 'Moderate' },
];

export default function Recommendations() {
  const [selectedType, setSelectedType] = useState<RecommendationType | 'ALL'>('ALL');
  const [selectedConfidence, setSelectedConfidence] = useState<ConfidenceLevel | 'ALL'>('ALL');
  const [daysAhead, setDaysAhead] = useState(7);

  const { data: recommendations, isLoading } = useQuery({
    queryKey: ['recommendations', daysAhead],
    queryFn: () => recommendationService.getAll(daysAhead),
  });

  const filteredRecommendations = recommendations?.filter((rec) => {
    const typeMatch = selectedType === 'ALL' || rec.type === selectedType;
    const confidenceMatch = selectedConfidence === 'ALL' || rec.confidence === selectedConfidence;
    return typeMatch && confidenceMatch;
  });

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Recommendations</h1>

      <div className={styles.filters}>
        <div className={styles.filterGroup}>
          <label className={styles.filterLabel}>Type</label>
          <select
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value as RecommendationType | 'ALL')}
            className={styles.select}
          >
            {RECOMMENDATION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel}>Confidence</label>
          <select
            value={selectedConfidence}
            onChange={(e) => setSelectedConfidence(e.target.value as ConfidenceLevel | 'ALL')}
            className={styles.select}
          >
            {CONFIDENCE_LEVELS.map((level) => (
              <option key={level.value} value={level.value}>
                {level.label}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.filterGroup}>
          <label className={styles.filterLabel}>Days Ahead</label>
          <select
            value={daysAhead}
            onChange={(e) => setDaysAhead(Number(e.target.value))}
            className={styles.select}
          >
            <option value={1}>1 Day</option>
            <option value={3}>3 Days</option>
            <option value={7}>7 Days</option>
            <option value={14}>14 Days</option>
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className={styles.loading}>Loading recommendations...</div>
      ) : filteredRecommendations && filteredRecommendations.length > 0 ? (
        <>
          <p className={styles.count}>
            Showing {filteredRecommendations.length} recommendation
            {filteredRecommendations.length !== 1 ? 's' : ''}
          </p>
          <div className={styles.grid}>
            {filteredRecommendations.map((rec) => (
              <RecommendationCard key={`${rec.fixtureId}-${rec.type}`} recommendation={rec} />
            ))}
          </div>
        </>
      ) : (
        <p className={styles.empty}>No recommendations found matching your filters.</p>
      )}
    </div>
  );
}
