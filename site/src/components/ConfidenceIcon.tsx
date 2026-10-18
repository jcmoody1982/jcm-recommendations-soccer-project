interface Props {
  level: string | null | undefined;
  isElite?: boolean;
  size?: number;
}

const chevron = (y: number, color: string) => (
  <path
    key={y}
    d={`M5.2 ${y + 3.2} 10 ${y} 14.8 ${y + 3.2}`}
    stroke={color}
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  />
);

/**
 * Rank tile per confidence tier: three stripes on gold for Elite, two on green for Strong,
 * one on an outlined tile for Moderate. The stripe count carries the tier without colour.
 */
export function ConfidenceIcon({ level, isElite = false, size = 16 }: Props) {
  const band = level?.toUpperCase();
  const label = isElite ? 'Elite pick' : band === 'STRONG' ? 'Strong' : band === 'MODERATE' ? 'Moderate' : 'Weak';

  let content;
  if (isElite) {
    content = (
      <>
        <rect x="1" y="1" width="18" height="18" rx="4" fill="var(--elite-bolt)" />
        {[4.6, 8.4, 12.2].map((y) => chevron(y, 'var(--elite-ink)'))}
      </>
    );
  } else if (band === 'STRONG') {
    content = (
      <>
        <rect x="1" y="1" width="18" height="18" rx="4" fill="var(--confidence-strong)" />
        {[6.4, 10.4].map((y) => chevron(y, '#ffffff'))}
      </>
    );
  } else if (band === 'MODERATE') {
    content = (
      <>
        <rect x="1.9" y="1.9" width="16.2" height="16.2" rx="3.4" stroke="var(--confidence-moderate)" strokeWidth="1.8" />
        {chevron(8.4, 'var(--confidence-moderate)')}
      </>
    );
  } else {
    content = (
      <>
        <rect x="1.9" y="1.9" width="16.2" height="16.2" rx="3.4" stroke="var(--text-muted)" strokeWidth="1.8" />
        <path d="M7 10h6" stroke="var(--text-muted)" strokeWidth="2" strokeLinecap="round" />
      </>
    );
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label={label}
      style={{ display: 'inline-block', flexShrink: 0, verticalAlign: 'middle' }}
    >
      <title>{label}</title>
      {content}
    </svg>
  );
}
