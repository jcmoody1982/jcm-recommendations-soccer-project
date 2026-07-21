import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { RecommendationCard } from '../components/RecommendationCard';
import { SummaryCard } from '../components/SummaryCard';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['recommendation-summary'],
    queryFn: () => recommendationService.getSummary(),
  });

  const { data: strongRecs, isLoading: recsLoading } = useQuery({
    queryKey: ['strong-recommendations'],
    queryFn: () => recommendationService.getStrong(),
  });

  if (summaryLoading || recsLoading) {
    return <div className={styles.loading}>Loading...</div>;
  }

  return (
    <div className={styles.dashboard}>
      <h1 className={styles.title}>Dashboard</h1>

      {summary && (
        <div className={styles.summaryGrid}>
          <SummaryCard
            title="Fixtures Analyzed"
            value={summary.fixturesAnalyzed}
            icon="📅"
          />
          <SummaryCard
            title="Total Recommendations"
            value={summary.totalRecommendations}
            icon="💡"
          />
          <SummaryCard
            title="Strong Picks"
            value={summary.strongRecommendations}
            icon="🎯"
            highlight
          />
          <SummaryCard
            title="Moderate Picks"
            value={summary.moderateRecommendations}
            icon="📊"
          />
        </div>
      )}

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Top Picks Today</h2>
        {strongRecs && strongRecs.length > 0 ? (
          <div className={styles.recommendationGrid}>
            {strongRecs.slice(0, 6).map((rec) => (
              <RecommendationCard key={`${rec.fixtureId}-${rec.type}`} recommendation={rec} />
            ))}
          </div>
        ) : (
          <p className={styles.empty}>No strong recommendations available.</p>
        )}
      </section>
    </div>
  );
}
