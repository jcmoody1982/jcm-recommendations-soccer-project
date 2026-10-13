import { describe, expect, it } from 'vitest';
import { comparePrice, formatEdge } from './pricing';

describe('comparePrice', () => {
  it('turns a probability into a fair price and an edge at the book price', () => {
    const result = comparePrice(62, 1.8);
    expect(result?.fairOdds).toBeCloseTo(1.613, 3);
    expect(result?.edgePct).toBeCloseTo(11.6, 1);
  });

  it('reports a negative edge when the book is shorter than our price', () => {
    expect(comparePrice(50, 1.8)?.edgePct).toBeCloseTo(-10, 5);
  });

  it('skips unpriced, degenerate prices and out-of-range scores', () => {
    expect(comparePrice(62, null)).toBeNull();
    expect(comparePrice(null, 1.8)).toBeNull();
    expect(comparePrice(62, 1.0)).toBeNull();
    expect(comparePrice(0, 1.8)).toBeNull();
    expect(comparePrice(100, 1.8)).toBeNull();
  });
});

describe('formatEdge', () => {
  it('rounds and signs', () => {
    expect(formatEdge(11.6)).toBe('+12%');
    expect(formatEdge(-10)).toBe('-10%');
    expect(formatEdge(0.2)).toBe('0%');
  });
});
