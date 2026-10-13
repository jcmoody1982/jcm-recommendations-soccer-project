import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { useShortlist } from '../contexts/ShortlistContext';
import { MarketIcon } from '../components';
import type { Recommendation } from '../types';
import {
  buildBoards,
  legOdds,
  legProbability,
  toWinAccumulator,
  topPicksAccumulator,
  type Accumulator,
} from '../utils/betGen';
import { elitePickKey } from '../utils/elitePicks';
import { formatKickoffDisplay, type KickoffWindow } from '../utils/kickoff';
import {
  COMPETITION_SCOPE_OPTIONS,
  CONFIDENCE_OPTIONS,
  type CompetitionScope,
  type ConfidenceFilter,
} from '../utils/recommendationFilters';
import { SECTION_CONFIG, sectionTitle } from '../utils/recommendationSections';
import { soccerFixturePath } from '../utils/sport';
import styles from './BetGen.module.css';

type Mode = 'top' | 'toWin';

const HORIZON_OPTIONS = [3, 7] as const;
const KICKOFF_OPTIONS: Array<{ value: KickoffWindow; label: string }> = [
  { value: 'all', label: 'All kickoffs' },
  { value: 'soon', label: 'Soon' },
  { value: 'today', label: 'Today' },
  { value: 'tomorrow', label: 'Tomorrow' },
];
const SELECTION_OPTIONS = Array.from({ length: 10 }, (_, i) => i + 1);

function parseAmount(value: string): number | null {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function formatMoney(amount: number): string {
  return `£${amount.toLocaleString('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatChance(probability: number): string {
  const pct = probability * 100;
  if (pct >= 10) return `${pct.toFixed(0)}%`;
  if (pct >= 1) return `${pct.toFixed(1)}%`;
  return `${pct.toFixed(2)}%`;
}

function ChipGroup<T extends string | number>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className={styles.chipGroup} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={String(option.value)}
          type="button"
          className={`${styles.chip} ${value === option.value ? styles.chipActive : ''}`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function LegRow({ leg, onRemove }: { leg: Recommendation; onRemove: () => void }) {
  const kickoff = formatKickoffDisplay(leg.matchDateUnix);
  const odds = legOdds(leg);
  const probability = legProbability(leg);
  const config = SECTION_CONFIG[leg.type];
  return (
    <div className={styles.leg}>
      <span className={styles.legBoard}>
        <MarketIcon type={leg.type} title={sectionTitle(leg.type)} />
        {sectionTitle(leg.type)}
      </span>
      <span className={styles.legWhen} title={kickoff.title}>
        {kickoff.primaryLabel} {kickoff.timeLabel}
      </span>
      <span className={styles.legFixture}>
        <Link to={soccerFixturePath(leg.fixtureId)}>
          {leg.homeTeamName} vs {leg.awayTeamName}
        </Link>
        {leg.leagueName && <span className={styles.legLeague}>{leg.leagueName}</span>}
      </span>
      <span className={styles.legSelection}>{leg.market}</span>
      <span className={styles.legOdds}>{odds != null ? odds.toFixed(2) : <em>No price</em>}</span>
      <span className={styles.legScore} title={config?.scoreLabel}>
        {probability != null
          ? `${leg.score.toFixed(0)}%`
          : `${config?.scoreLabel ?? 'Score'} ${leg.score.toFixed(0)}${config?.scoreUnit ?? ''}`}
      </span>
      <button type="button" className={styles.legRemove} onClick={onRemove} title="Remove this pick">
        ✕
      </button>
    </div>
  );
}

function AccumulatorCard({
  acca,
  stake,
  onRemove,
}: {
  acca: Accumulator;
  stake: number | null;
  onRemove: (leg: Recommendation) => void;
}) {
  const { isShortlisted, toggleShortlist } = useShortlist();
  const unpriced = acca.legs.length - acca.pricedLegs;
  const allShortlisted = acca.legs.every((leg) => isShortlisted(leg.fixtureId, leg.type));

  const addAllToShortlist = () => {
    for (const leg of acca.legs) {
      if (!isShortlisted(leg.fixtureId, leg.type)) toggleShortlist(leg.fixtureId, leg.type);
    }
  };

  return (
    <div className={styles.card}>
      <div className={styles.summary}>
        <div className={styles.stat}>
          <span className={styles.statLabel}>Selections</span>
          <span className={styles.statValue}>{acca.legs.length}</span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>Combined odds</span>
          <span className={styles.statValue}>
            {acca.combinedOdds != null ? acca.combinedOdds.toFixed(2) : '—'}
          </span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>Return{stake != null ? ` on ${formatMoney(stake)}` : ''}</span>
          <span className={styles.statValue}>
            {acca.combinedOdds != null && stake != null ? formatMoney(acca.combinedOdds * stake) : '—'}
          </span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statLabel}>Our chance it lands</span>
          <span className={styles.statValue}>
            {acca.probability != null ? formatChance(acca.probability) : '—'}
          </span>
        </div>
      </div>

      {unpriced > 0 && (
        <p className={styles.note}>
          {unpriced} selection{unpriced !== 1 ? 's have' : ' has'} no price from us. Check {unpriced !== 1 ? 'them' : 'it'} with
          your bookmaker; combined odds and return cover the {acca.pricedLegs} priced selection
          {acca.pricedLegs !== 1 ? 's' : ''} only.
        </p>
      )}
      {acca.probabilityLegs > 0 && acca.probabilityLegs < acca.legs.length && (
        <p className={styles.note}>
          Our chance covers the {acca.probabilityLegs} selection{acca.probabilityLegs !== 1 ? 's' : ''} scored as a
          probability; the others are scored differently.
        </p>
      )}

      <div className={styles.legHeader}>
        <span>Market</span>
        <span>Kickoff</span>
        <span>Fixture</span>
        <span>Selection</span>
        <span>Price</span>
        <span>Our view</span>
        <span></span>
      </div>
      <div className={styles.legs}>
        {acca.legs.map((leg) => (
          <LegRow key={elitePickKey(leg.fixtureId, leg.type)} leg={leg} onRemove={() => onRemove(leg)} />
        ))}
      </div>

      <div className={styles.cardActions}>
        <button type="button" className={styles.primaryButton} onClick={addAllToShortlist} disabled={allShortlisted}>
          {allShortlisted ? 'All on your Shortlist' : 'Add all to Shortlist'}
        </button>
      </div>
    </div>
  );
}

export default function BetGen() {
  const [mode, setMode] = useState<Mode>('top');
  const [horizon, setHorizon] = useState<number>(7);
  const [kickoffWindow, setKickoffWindow] = useState<KickoffWindow>('all');
  const [confidence, setConfidence] = useState<ConfidenceFilter>('strong');
  const [scope, setScope] = useState<CompetitionScope>('all');
  const [stakeInput, setStakeInput] = useState('10');
  const [targetInput, setTargetInput] = useState('100');
  const [selections, setSelections] = useState(4);
  const [excluded, setExcluded] = useState<Set<string>>(() => new Set());

  const { data: grouped, isLoading, isError, refetch } = useQuery({
    queryKey: ['recommendations-grouped', horizon],
    queryFn: () => recommendationService.getGrouped(horizon),
  });

  const boards = useMemo(
    () => buildBoards(grouped, { confidence, kickoffWindow, scope }, excluded),
    [grouped, confidence, kickoffWindow, scope, excluded]
  );

  const stake = parseAmount(stakeInput);
  const targetReturn = parseAmount(targetInput);
  const targetOdds = stake != null && targetReturn != null ? targetReturn / stake : null;

  const topAcca = useMemo(() => topPicksAccumulator(boards), [boards]);
  const toWin = useMemo(
    () => (mode === 'toWin' && targetOdds != null && targetOdds > 1
      ? toWinAccumulator(boards, targetOdds, selections)
      : null),
    [mode, boards, targetOdds, selections]
  );

  const removeLeg = (leg: Recommendation) => {
    setExcluded((prev) => new Set(prev).add(elitePickKey(leg.fixtureId, leg.type)));
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Bet Gen</h1>
      </header>

      <div className={styles.filters}>
        <ChipGroup label="Competition scope" options={COMPETITION_SCOPE_OPTIONS} value={scope} onChange={setScope} />
        <ChipGroup
          label="Days ahead"
          options={HORIZON_OPTIONS.map((days) => ({ value: days as number, label: `${days} days` }))}
          value={horizon}
          onChange={setHorizon}
        />
        <ChipGroup label="Kickoff window" options={KICKOFF_OPTIONS} value={kickoffWindow} onChange={setKickoffWindow} />
        <ChipGroup label="Confidence" options={CONFIDENCE_OPTIONS} value={confidence} onChange={setConfidence} />
      </div>

      <div className={styles.modes} role="tablist" aria-label="Bet builder">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'top'}
          className={`${styles.modeTab} ${mode === 'top' ? styles.modeTabActive : ''}`}
          onClick={() => setMode('top')}
        >
          Build My Bet From Top Picks
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'toWin'}
          className={`${styles.modeTab} ${mode === 'toWin' ? styles.modeTabActive : ''}`}
          onClick={() => setMode('toWin')}
        >
          Build My Bet To Win
        </button>
      </div>

      <p className={styles.blurb}>
        {mode === 'top'
          ? 'The top pick on every Recommendations board, one selection per match, as a single accumulator.'
          : 'The selections, one per match, whose combined price reaches your target with the best chance of all landing. Only priced picks scored as a probability are used.'}
      </p>

      <div className={styles.inputs}>
        <label className={styles.field}>
          <span>Stake (£)</span>
          <input type="number" min="0" step="0.5" inputMode="decimal" value={stakeInput} onChange={(e) => setStakeInput(e.target.value)} />
        </label>
        {mode === 'toWin' && (
          <>
            <label className={styles.field}>
              <span>Target return (£, incl. stake)</span>
              <input type="number" min="0" step="1" inputMode="decimal" value={targetInput} onChange={(e) => setTargetInput(e.target.value)} />
            </label>
            <label className={styles.field}>
              <span>Selections</span>
              <select value={selections} onChange={(e) => setSelections(Number(e.target.value))}>
                {SELECTION_OPTIONS.map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </label>
            {targetOdds != null && targetOdds > 1 && (
              <span className={styles.targetOdds}>Needs combined odds of {targetOdds.toFixed(2)}</span>
            )}
          </>
        )}
        {excluded.size > 0 && (
          <button type="button" className={styles.resetButton} onClick={() => setExcluded(new Set())}>
            Restore {excluded.size} removed pick{excluded.size !== 1 ? 's' : ''}
          </button>
        )}
      </div>

      {isLoading ? (
        <p className={styles.status}>Loading picks…</p>
      ) : isError ? (
        <p className={styles.status}>
          Couldn&apos;t load picks.{' '}
          <button type="button" className={styles.linkButton} onClick={() => refetch()}>Try again</button>
        </p>
      ) : mode === 'top' ? (
        topAcca.legs.length > 0 ? (
          <AccumulatorCard acca={topAcca} stake={stake} onRemove={removeLeg} />
        ) : (
          <p className={styles.status}>No picks match these filters. Try Strong + Moderate or a wider kickoff window.</p>
        )
      ) : stake == null || targetReturn == null ? (
        <p className={styles.status}>Enter a stake and a target return.</p>
      ) : targetOdds != null && targetOdds <= 1 ? (
        <p className={styles.status}>The target return must be more than the stake.</p>
      ) : toWin?.status === 'built' ? (
        <AccumulatorCard acca={toWin.acca} stake={stake} onRemove={removeLeg} />
      ) : toWin?.status === 'unreachable' ? (
        <p className={styles.status}>
          {toWin.maxOdds == null
            ? `There aren't ${selections} matches with a priced pick under these filters (${toWin.candidates} priced pick${toWin.candidates !== 1 ? 's' : ''} available). Try fewer selections or wider filters.`
            : `${selections} selection${selections !== 1 ? 's' : ''} can reach combined odds of ${toWin.maxOdds.toFixed(2)} at most (${formatMoney(toWin.maxOdds * stake)} on ${formatMoney(stake)}), short of the ${toWin.targetOdds.toFixed(2)} needed. Add selections or lower the target.`}
        </p>
      ) : null}

      <p className={styles.responsible}>
        18+ only. These are statistical estimates, not guarantees, and every extra selection makes an
        accumulator less likely to land. Only stake what you can afford to lose. For help, visit{' '}
        <a href="https://www.begambleaware.org" target="_blank" rel="noopener noreferrer">BeGambleAware.org</a>.
      </p>
    </div>
  );
}
