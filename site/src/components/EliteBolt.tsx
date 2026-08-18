import styles from './EliteBolt.module.css';

interface Props {
  size?: number;
  className?: string;
  /** Circle badge matching Strong/Moderate pick icons. */
  variant?: 'plain' | 'badge';
}

/** Lightning mark for Elite picks. Badge variant replaces the Strong check icon. */
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
          <circle cx="10" cy="10" r="9" fill="var(--elite-bolt)" />
          <path
            d="M11.1 4.2 6.2 10.8h3.4L8.3 15.8l6.2-8H11.4L11.1 4.2Z"
            fill="white"
            stroke="white"
            strokeWidth="0.6"
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
