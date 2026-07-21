import styles from './SummaryCard.module.css';

interface Props {
  title: string;
  value: number;
  icon: string;
  highlight?: boolean;
}

export function SummaryCard({ title, value, icon, highlight }: Props) {
  return (
    <div className={`${styles.card} ${highlight ? styles.highlight : ''}`}>
      <div className={styles.icon}>{icon}</div>
      <div className={styles.content}>
        <div className={styles.value}>{value}</div>
        <div className={styles.title}>{title}</div>
      </div>
    </div>
  );
}
