import styles from './EliteBolt.module.css';

interface Props {
  size?: number;
  className?: string;
  /** Medal rings + bolt in the pick-row icon slot. */
  variant?: 'plain' | 'badge';
}

/** Lightning mark for Elite picks. Badge variant is a double gold ring (medal). */
export function EliteBolt({ size = 14, className, variant = 'plain' }: Props) {
  if (variant === 'badge') {
    return (
      <span
        className={`${styles.bolt} ${className ?? ''}`.trim()}
        title="Elite pick"
        aria-label="Elite pick"
        role="img"
      >
        <svg
          width={size}
          height={size}
          viewBox="0 0 20 20"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden
        >
          <circle cx="10" cy="10" r="9" stroke="currentColor" strokeWidth="1.7" />
          <circle cx="10" cy="10" r="6.1" stroke="currentColor" strokeWidth="1.25" />
          <path
            d="M10.85 5.2 7.55 10.15h2.25L8.9 14.7l4.35-5.7H11.05L10.85 5.2Z"
            fill="currentColor"
            stroke="currentColor"
            strokeWidth="0.4"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    );
  }

  return (
    <span
      className={`${styles.bolt} ${className ?? ''}`.trim()}
      title="Elite pick"
      aria-label="Elite pick"
      role="img"
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 20 20"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        <path
          d="M11.2 2.2 4.4 11.4h5.1L7.6 17.8l8.3-10.6H10.7L11.2 2.2Z"
          fill="currentColor"
          stroke="currentColor"
          strokeWidth="1.1"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  );
}
