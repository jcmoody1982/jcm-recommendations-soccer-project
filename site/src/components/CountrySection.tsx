import type { CountryGroup } from '../types';
import { CompetitionCard } from './CompetitionCard';
import styles from './CountrySection.module.css';

interface CountrySectionProps {
  countryGroup: CountryGroup;
}

const FLAG_EMOJIS: Record<string, string> = {
  'GB-ENG': '🏴󠁧󠁢󠁥󠁮󠁧󠁿',
  'GB-SCT': '🏴󠁧󠁢󠁳󠁣󠁴󠁿',
  'DE': '🇩🇪',
  'ES': '🇪🇸',
  'IT': '🇮🇹',
  'FR': '🇫🇷',
  'NL': '🇳🇱',
  'PT': '🇵🇹',
  'BE': '🇧🇪',
  'TR': '🇹🇷',
  'GR': '🇬🇷',
  'AT': '🇦🇹',
  'CH': '🇨🇭',
  'DK': '🇩🇰',
  'NO': '🇳🇴',
  'SE': '🇸🇪',
  'RU': '🇷🇺',
  'UA': '🇺🇦',
  'PL': '🇵🇱',
  'CZ': '🇨🇿',
  'BR': '🇧🇷',
  'AR': '🇦🇷',
  'MX': '🇲🇽',
  'US': '🇺🇸',
  'AU': '🇦🇺',
  'JP': '🇯🇵',
  'CN': '🇨🇳',
  'KR': '🇰🇷',
};

export function CountrySection({ countryGroup }: CountrySectionProps) {
  const flag = FLAG_EMOJIS[countryGroup.countryCode] || '🏳️';

  return (
    <section className={styles.section}>
      <h2 className={styles.countryHeader}>
        <span className={styles.flag}>{flag}</span>
        <span className={styles.countryName}>{countryGroup.country}</span>
      </h2>
      <div className={styles.competitions}>
        {countryGroup.competitions.map((competition) => (
          <CompetitionCard key={competition.leagueId} competition={competition} />
        ))}
      </div>
    </section>
  );
}
