import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { toEliteKeySet } from '../utils/elitePicks';

/** Server-selected Elite Picks for the window, plus `fixtureId:type` keys for row badges. */
export function useElitePicks(daysAhead = 7) {
  const { data: picks = [] } = useQuery({
    queryKey: ['recommendations-elite', daysAhead],
    queryFn: () => recommendationService.getElite(daysAhead),
  });
  const keys = useMemo(() => toEliteKeySet(picks), [picks]);
  return { picks, keys };
}
