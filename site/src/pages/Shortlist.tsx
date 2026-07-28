import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useShortlist } from '../contexts/ShortlistContext';
import { recommendationService } from '../services/api';
import { RecommendationRow, ExportModal } from '../components';
import { RecommendationIcons, ChartIcon, ExportIcon, StarOutlineIcon } from '../components/Icons';
import type { Recommendation, RecommendationType } from '../types';
import styles from './Shortlist.module.css';

const SECTION_CONFIG: Record<RecommendationType, { title: string; showPrice: boolean }> = {
  BTTS: { title: 'Both Teams To Score', showPrice: true },
  OVER_GOALS: { title: 'Over Goals', showPrice: true },
  UNDER_GOALS: { title: 'Under Goals', showPrice: true },
  BOOKING_POINTS: { title: 'Booking Points', showPrice: false },
  VALUE_BET: { title: 'Value Bets', showPrice: true },
  WINNING_FORM_MISMATCH: { title: 'Winning Form Mismatch', showPrice: false },
  LOSING_FORM_MISMATCH: { title: 'Losing Form Mismatch', showPrice: false },
  OVER_CORNERS: { title: 'Over Corners', showPrice: false },
  UNDER_CORNERS: { title: 'Under Corners', showPrice: false },
  CLEAN_SHEET: { title: 'Clean Sheet', showPrice: false },
  FIRST_HALF_GOALS: { title: 'First Half Goals', showPrice: false },
  SECOND_HALF_GOALS: { title: 'Second Half Goals', showPrice: false },
  MATCH_RESULT: { title: 'Match Result', showPrice: true },
  HOME_AWAY_SPECIALIST: { title: 'Home/Away Specialist', showPrice: false },
  DRAW: { title: 'Draw', showPrice: true },
  DOUBLE_CHANCE: { title: 'Double Chance', showPrice: false },
  RESULT_BTTS: { title: 'Result + BTTS', showPrice: false },
  TOP_VS_BOTTOM: { title: 'Top vs Bottom', showPrice: false },
};

export default function Shortlist() {
  const { shortlist, clearShortlist } = useShortlist();
  const [showExportModal, setShowExportModal] = useState(false);

  const { data: allRecommendations, isLoading } = useQuery({
    queryKey: ['recommendations-grouped', 7],
    queryFn: () => recommendationService.getGrouped(7),
  });

  const shortlistedRecommendations: Recommendation[] = [];

  if (allRecommendations) {
    for (const item of shortlist) {
      const typeRecs = allRecommendations[item.type as RecommendationType] || [];
      const match = typeRecs.find(
        (rec: Recommendation) => rec.fixtureId === item.fixtureId && rec.type === item.type
      );
      if (match) {
        shortlistedRecommendations.push(match);
      }
    }
  }

  const groupedByType = shortlistedRecommendations.reduce((acc, rec) => {
    const type = rec.type as RecommendationType;
    if (!acc[type]) {
      acc[type] = [];
    }
    acc[type].push(rec);
    return acc;
  }, {} as Record<RecommendationType, Recommendation[]>);

  const sortedTypes = Object.keys(groupedByType).sort() as RecommendationType[];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Shortlist</h1>
        <div className={styles.headerActions}>
          {shortlistedRecommendations.length > 0 && (
            <button 
              className={styles.exportButton} 
              onClick={() => setShowExportModal(true)}
              aria-label="Export shortlist"
            >
              <ExportIcon size={16} /> Export
            </button>
          )}
          {shortlist.length > 0 && (
            <button className={styles.clearButton} onClick={clearShortlist}>
              Clear All
            </button>
          )}
        </div>
      </header>

      {isLoading ? (
        <div className={styles.loading}>Loading shortlist...</div>
      ) : shortlistedRecommendations.length > 0 ? (
        <div className={styles.sections}>
          {sortedTypes.map((type) => {
            const recommendations = groupedByType[type];
            const config = SECTION_CONFIG[type] || { title: type, showPrice: true };
            const IconComponent = RecommendationIcons[type] || ChartIcon;
            
            return (
              <section key={type} className={styles.section}>
                <h2 className={styles.sectionHeader}>
                  <span className={styles.sectionIcon}><IconComponent size={22} /></span>
                  <span className={styles.sectionTitle}>{config.title}</span>
                  <span className={styles.sectionCount}>{recommendations.length} pick{recommendations.length !== 1 ? 's' : ''}</span>
                </h2>
                <div className={styles.list}>
                  {recommendations.map((rec) => (
                    <RecommendationRow
                      key={`${rec.fixtureId}-${rec.type}`}
                      recommendation={rec}
                      showPrice={config.showPrice}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      ) : (
        <div className={styles.empty}>
          <span className={styles.emptyIcon}><StarOutlineIcon size={48} /></span>
          <h2>No picks shortlisted</h2>
          <p>Add recommendations to your shortlist by clicking the star icon on any pick.</p>
        </div>
      )}

      <footer className={styles.footer}>
        <span className={styles.totalCount}>{shortlist.length} shortlisted pick{shortlist.length !== 1 ? 's' : ''}</span>
      </footer>

      <ExportModal
        isOpen={showExportModal}
        onClose={() => setShowExportModal(false)}
        recommendations={shortlistedRecommendations}
      />
    </div>
  );
}
