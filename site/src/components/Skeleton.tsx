import styles from './Skeleton.module.css';

interface SkeletonProps {
  width?: string;
  height?: string;
  borderRadius?: string;
  className?: string;
}

export function Skeleton({ 
  width = '100%', 
  height = '1rem', 
  borderRadius = '4px',
  className = ''
}: SkeletonProps) {
  return (
    <div 
      className={`${styles.skeleton} ${className}`}
      style={{ width, height, borderRadius }}
    />
  );
}

export function RecommendationSectionSkeleton() {
  return (
    <div className={styles.sectionSkeleton}>
      <div className={styles.sectionHeader}>
        <Skeleton width="24px" height="24px" borderRadius="4px" />
        <Skeleton width="180px" height="24px" />
        <Skeleton width="60px" height="20px" borderRadius="10px" />
      </div>
      <div className={styles.tableHeader}>
        <Skeleton width="100%" height="32px" />
      </div>
      <div className={styles.rows}>
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className={styles.row}>
            <Skeleton width="24px" height="24px" borderRadius="50%" />
            <Skeleton width="32px" height="32px" borderRadius="4px" />
            <Skeleton width="80px" height="16px" />
            <Skeleton width="200px" height="16px" />
            <Skeleton width="120px" height="16px" />
            <Skeleton width="50px" height="16px" />
            <Skeleton width="50px" height="16px" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function RecommendationsPageSkeleton() {
  return (
    <div className={styles.pageSkeleton}>
      {[1, 2, 3].map((i) => (
        <RecommendationSectionSkeleton key={i} />
      ))}
    </div>
  );
}

export function CompetitionCardSkeleton() {
  return (
    <div className={styles.competitionCard}>
      <Skeleton width="40px" height="40px" borderRadius="8px" />
      <div className={styles.competitionInfo}>
        <Skeleton width="180px" height="18px" />
        <Skeleton width="80px" height="14px" />
      </div>
      <Skeleton width="30px" height="24px" borderRadius="12px" />
    </div>
  );
}

export function DashboardSkeleton() {
  return (
    <div className={styles.dashboardSkeleton}>
      {[1, 2, 3].map((countryIdx) => (
        <div key={countryIdx} className={styles.countrySkeleton}>
          <div className={styles.countryHeader}>
            <Skeleton width="24px" height="24px" />
            <Skeleton width="120px" height="20px" />
          </div>
          <div className={styles.competitions}>
            {[1, 2].map((compIdx) => (
              <CompetitionCardSkeleton key={compIdx} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
