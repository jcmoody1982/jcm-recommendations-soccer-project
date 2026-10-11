import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ShortlistProvider, useShortlist } from './ShortlistContext';

const LEGACY_KEY = 'accabaccaglory-shortlist';
const KEY = 'accabaccaglory-shortlist-soccer';

const wrapper = ({ children }: { children: ReactNode }) => <ShortlistProvider>{children}</ShortlistProvider>;

afterEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('ShortlistContext', () => {
  it('starts empty and toggles items in and out', () => {
    const { result } = renderHook(() => useShortlist(), { wrapper });
    expect(result.current.shortlistCount).toBe(0);

    act(() => result.current.toggleShortlist(1, 'BTTS'));
    expect(result.current.isShortlisted(1, 'BTTS')).toBe(true);
    expect(result.current.isShortlisted(1, 'DRAW')).toBe(false);
    expect(JSON.parse(localStorage.getItem(KEY)!)).toHaveLength(1);

    act(() => result.current.toggleShortlist(1, 'BTTS'));
    expect(result.current.shortlistCount).toBe(0);
  });

  it('removes a single item and clears all', () => {
    const { result } = renderHook(() => useShortlist(), { wrapper });
    act(() => {
      result.current.toggleShortlist(1, 'BTTS');
      result.current.toggleShortlist(2, 'DRAW');
    });
    expect(result.current.shortlistCount).toBe(2);

    act(() => result.current.removeFromShortlist(1, 'BTTS'));
    expect(result.current.shortlist.map((i) => i.fixtureId)).toEqual([2]);

    act(() => result.current.clearShortlist());
    expect(result.current.shortlistCount).toBe(0);
  });

  it('migrates the pre-sport legacy key once', () => {
    const legacy = [{ fixtureId: 9, type: 'BTTS', addedAt: '2026-10-01T00:00:00Z' }];
    localStorage.setItem(LEGACY_KEY, JSON.stringify(legacy));

    const { result } = renderHook(() => useShortlist(), { wrapper });

    expect(result.current.isShortlisted(9, 'BTTS')).toBe(true);
    expect(localStorage.getItem(LEGACY_KEY)).toBeNull();
    expect(JSON.parse(localStorage.getItem(KEY)!)).toEqual(legacy);
  });

  it('prefers the soccer key over a stale legacy key', () => {
    localStorage.setItem(KEY, JSON.stringify([{ fixtureId: 1, type: 'DRAW', addedAt: '' }]));
    localStorage.setItem(LEGACY_KEY, JSON.stringify([{ fixtureId: 2, type: 'BTTS', addedAt: '' }]));

    const { result } = renderHook(() => useShortlist(), { wrapper });

    expect(result.current.shortlist.map((i) => i.fixtureId)).toEqual([1]);
    expect(localStorage.getItem(LEGACY_KEY)).not.toBeNull();
  });

  it('recovers from corrupt storage with an empty list', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    localStorage.setItem(KEY, '{not json');

    const { result } = renderHook(() => useShortlist(), { wrapper });

    expect(result.current.shortlistCount).toBe(0);
  });

  it('throws outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => renderHook(() => useShortlist())).toThrow(/ShortlistProvider/);
  });
});
