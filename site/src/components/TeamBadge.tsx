import { useState } from 'react';
import styles from './TeamBadge.module.css';

interface Props {
  src?: string | null;
  size?: number;
}

export function TeamBadge({ src, size = 16 }: Props) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) return null;
  return (
    <img
      src={src}
      alt=""
      width={size}
      height={size}
      loading="lazy"
      className={styles.badge}
      onError={() => setFailed(true)}
    />
  );
}
