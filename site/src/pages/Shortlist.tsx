import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useShortlist } from '../contexts/ShortlistContext';
import { recommendationService } from '../services/api';
import { RecommendationRow, ExportModal } from '../components';
import type { Recommendation, RecommendationType } from '../types';
import { SECTION_CONFIG } from '../utils/recommendationSections';
import styles from './Shortlist.module.css';

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
              📤 Export
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
            const config = SECTION_CONFIG[type] || {
              title: type,
              icon: '📊',
              showPrice: true,
              scoreLabel: 'Score',
              scoreUnit: '',
            };

            return (
              <section key={type} className={styles.section}>
                <h2 className={styles.sectionHeader}>
                  <span className={styles.sectionIcon}>{config.icon}</span>
                  <span className={styles.sectionTitle}>{config.title}</span>
                  <span className={styles.sectionCount}>
                    {recommendations.length} pick{recommendations.length !== 1 ? 's' : ''}
                  </span>
                </h2>
                <div className={styles.list}>
                  {recommendations.map((rec) => (
                    <RecommendationRow
                      key={`${rec.fixtureId}-${rec.type}`}
                      recommendation={rec}
                      showPrice={config.showPrice}
                      showPositionGap={Boolean(config.showPositionGap)}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      ) : (
        <div className={styles.empty}>
          <span className={styles.emptyIcon}>☆</span>
          <h2>No picks shortlisted</h2>
          <p>Add recommendations to your shortlist by clicking the star icon on any pick.</p>
        </div>
      )}

      <footer className={styles.footer}>
        <span className={styles.totalCount}>
          {shortlist.length} shortlisted pick{shortlist.length !== 1 ? 's' : ''}
        </span>
      </footer>

      <ExportModal
        isOpen={showExportModal}
        onClose={() => setShowExportModal(false)}
        recommendations={shortlistedRecommendations}
      />
    </div>
  );
}
