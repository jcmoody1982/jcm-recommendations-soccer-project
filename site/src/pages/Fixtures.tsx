import { useQuery } from '@tanstack/react-query';
import { fixtureService } from '../services/api';
import { FixtureCard } from '../components/FixtureCard';
import styles from './Fixtures.module.css';

export default function Fixtures() {
  const { data: fixtures, isLoading, error } = useQuery({
    queryKey: ['fixtures'],
    queryFn: () => fixtureService.getUpcoming(),
  });

  if (isLoading) {
    return <div className={styles.loading}>Loading fixtures...</div>;
  }

  if (error) {
    return <div className={styles.error}>Failed to load fixtures.</div>;
  }

  const groupedFixtures = fixtures?.reduce(
    (acc, fixture) => {
      const date = new Date(fixture.dateUnix * 1000).toLocaleDateString('en-GB', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
      });
      if (!acc[date]) {
        acc[date] = [];
      }
      acc[date].push(fixture);
      return acc;
    },
    {} as Record<string, typeof fixtures>
  );

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Upcoming Fixtures</h1>

      {groupedFixtures && Object.keys(groupedFixtures).length > 0 ? (
        Object.entries(groupedFixtures).map(([date, dateFixtures]) => (
          <section key={date} className={styles.dateSection}>
            <h2 className={styles.dateTitle}>{date}</h2>
            <div className={styles.fixtureList}>
              {dateFixtures?.map((fixture) => (
                <FixtureCard key={fixture.id} fixture={fixture} />
              ))}
            </div>
          </section>
        ))
      ) : (
        <p className={styles.empty}>No upcoming fixtures available.</p>
      )}
    </div>
  );
}
