import styles from './ProbabilityBar.module.css';

interface Props {
  /** Our win probability, in percent. */
  ourPct: number;
  /** Win probability the book price implies, in percent. */
  impliedPct: number;
  className?: string;
}

const clampPct = (value: number) => Math.max(0, Math.min(100, value));

/**
 * Our probability as a filled bar with the book's implied probability as a tick, so the edge
 * reads as the green stretch past the tick (or the grey shortfall before it).
 */
export function ProbabilityBar({ ourPct, impliedPct, className }: Props) {
  const ours = clampPct(ourPct);
  const book = clampPct(impliedPct);
  const base = Math.min(ours, book);
  const label = `Ours ${Math.round(ours)}% vs book ${Math.round(book)}%`;

  return (
    <span className={`${styles.track} ${className ?? ''}`} role="img" aria-label={label} title={label}>
      <span className={styles.fill} style={{ width: `${base}%` }} />
      {ours > book ? (
        <span className={styles.edge} style={{ left: `${book}%`, width: `${ours - book}%` }} />
      ) : (
        <span className={styles.shortfall} style={{ left: `${ours}%`, width: `${book - ours}%` }} />
      )}
      <span className={styles.marker} style={{ left: `${book}%` }} />
    </span>
  );
}
