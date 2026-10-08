import { Link } from 'react-router-dom';
import { SOCCER_RECOMMENDATIONS } from '../utils/sport';
import styles from './UsFootball.module.css';

export default function UsFootball() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>US Football</h1>
      <p className={styles.message}>US Football is not available yet.</p>
      <Link to={SOCCER_RECOMMENDATIONS} className={styles.link}>
        Back to Soccer
      </Link>
    </div>
  );
}
