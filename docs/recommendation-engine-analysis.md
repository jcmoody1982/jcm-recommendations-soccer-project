# Recommendation Engines Analysis & Improvement Recommendations

**Date:** September 2026  
**Scope:** Review of all recommendation engines, calibration status, and improvement opportunities

---

## Executive Summary

This analysis covers the 18+ recommendation engines in the soccer betting predictions system. Based on code review and documented calibration data, several engines show calibration gaps where published scores don't match actual hit rates. The primary issues fall into three categories:

1. **Paused engines** requiring recalibration (Away tips, Draw value, Clean Sheet)
2. **Calibration drift** where claimed probabilities diverge from observed outcomes
3. **Structural improvements** to scoring methodology and thresholds

---

## Current Engine Status

### Fully Active Engines ✅

| Engine | Type | Last Calibrated | Notes |
|--------|------|-----------------|-------|
| BTTS | Goals | Aug 2026 | Multiplicative conjunction, shrinkage applied |
| Over 1.5 Goals | Goals | Aug 2026 | Poisson + empirical blend |
| Over 2.5 Goals | Goals | Aug 2026 | Lower thresholds for lower base rate |
| Over 0.5 Goals | Goals | Aug 2026 | High base rate market |
| Under Goals | Goals | - | Dynamic line selection |
| Corners | Props | - | Over/Under corner counts |
| Booking Points | Props | Aug 2026 | Cards potential scaled correctly |
| Player to Score | Player Props | Aug 2026 | Per-90 rates with venue splits |
| Player to Assist | Player Props | Aug 2026 | Per-90 rates with venue splits |
| Draw | Match | Aug 2026 | Complete rewrite - Poisson-based |
| Double Chance | Match | Aug 2026 | Fortress/traveler adjustments |
| Top vs Bottom | Match | Aug 2026 | Table position gaps |
| Form Mismatch | Match | Aug 2026 | PPG divergence detection |
| Clean Sheet | Defensive | Aug 2026 | Recalibrated from ~21% hit rate |
| Value Bet | Meta | Aug 2026 | Home win only (Away/Draw paused) |

### Paused/Restricted Markets ⚠️

| Engine | Restriction | Reason |
|--------|-------------|--------|
| Match Result | Away tips paused | Pending recalibration |
| Value Bet | Away Win paused | Poor snapshot hit rates |
| Value Bet | Draw paused | Aligned with Match Result |
| Result BTTS | - | Complex market combinations |

---

## Identified Issues by Engine

### 1. Draw Engine (UC-019)

**Historical Issue:** The previous weighted index was anti-predictive - the 60-69 band claimed 64.7% and returned 12.1%.

**Root Cause Analysis:**
- Draw frequency barely persists between fixtures
- A three-draw streak plus "specialist" label could nearly double the score
- Raising the threshold would have selected harder for noise

**Current Solution (Implemented):**
```java
// P(draw) now comes from scoreline distribution
double modelDrawPct = poissonDrawProbability(expectation.home(), expectation.away());
double blendedDrawPct = (modelDrawPct * 0.45) + (marketDrawPct * 0.55);
```

**Recommended Improvements:**
1. Consider adding recent H2H draw frequency as a small signal (~5-10% weight)
2. Implement league-specific draw rate priors (some leagues are more draw-heavy)
3. Add a "draw volatility" factor based on scoreline variance

### 2. Clean Sheet Engine (UC-014)

**Historical Issue:** ~21% hit rate when publishing 70+ scores.

**Root Cause Analysis:**
- Previous scoring summed 8 ratings with 6 sequential multipliers (up to ×1.82)
- Base rate is ~32% (home) and ~23% (away)
- A score of 70 was never achievable against these base rates

**Current Solution (Implemented):**
```java
// P(clean sheet) = exp(-lambda) under Poisson
double cleanSheetPct = Math.exp(-lambda) * 100.0;
// Thresholds: STRONG >= 48%, MODERATE >= 38%
```

**Recommended Improvements:**
1. Add opponent's last 5 games scoring trend as form lambda adjustment
2. Consider weather/pitch conditions if available (wet pitches = fewer goals)
3. Implement keeper-specific save rate data when available

### 3. Player Props (To Score / To Assist)

**Historical Issue:** Player prop boards published ~80% claims at 10-19% strike rate.

**Root Cause Analysis:**
- Old per-90 rates were published as-is without probability conversion
- A 0.55 goals/90 elite striker is only ~42% to score per full game
- Thresholds at 72/58 were unreachable by construction

**Current Solution (Implemented):**
```java
// Probability from Poisson: P(goals >= 1) = 1 - e^(-rate * minutes/90)
private static final PropSpec SPEC = new PropSpec(
    RecommendationType.PLAYER_TO_SCORE,
    "to score",
    0.25,  // min per90 to consider
    0.55,  // max realistic per90
    0.18,  // fallback rate
    33.0,  // Strong threshold (%)
    25.0   // Moderate threshold (%)
);
```

**Recommended Improvements:**
1. Factor in expected playing time from lineup predictions
2. Add opponent defensive xGA as a matchup modifier
3. Consider penalty-taking duties (significant for goal scorers)
4. Track recent form (last 3-5 games) vs season average divergence

### 4. Match Result Engine (UC-017)

**Current Restriction:** Away tips paused pending recalibration.

**Analysis:**
- Away win prediction is fundamentally harder (lower base rate ~26% vs ~46% home)
- Home advantage creates asymmetric accuracy between home/away predictions

**Recommended Improvements:**
1. Implement separate away-specific thresholds (higher bar for publication)
2. Add "road warrior" detection for teams with exceptionally good away records
3. Consider travel distance and fixture congestion as signals
4. Weight xG data more heavily for away predictions (more signal, less noise)

### 5. Value Bet Engine (UC-022)

**Current Restrictions:** Away Win and Draw markets paused.

**Recommended Improvements:**
1. Implement market-specific edge thresholds:
   - Home Win: current MIN_EDGE = 3%
   - Away Win: suggested MIN_EDGE = 8% (higher variance)
   - Draw: suggested MIN_EDGE = 10% (hardest to predict)

2. Add closing line value (CLV) tracking:
```java
// Track if our picks beat the closing price
record CLVMetrics(double avgOpenerOdds, double avgCloserOdds, double clvPercentage) {}
```

3. Consider odds movement as a signal (sharp money indicators)

---

## Calibration Analysis

### Calibration Bands System

The `ResultsPerformanceService` tracks calibration using reliability bands:

```java
private static final int[] CALIBRATION_BAND_FLOORS = {0, 50, 60, 70, 80, 90};
```

**Key Metric:** `gap = hitRate - avgScore`

- **Negative gap**: Engine overstates probability (overconfident)
- **Positive gap**: Engine understates probability (underconfident)

### Types with Probability Scoring (Eligible for Calibration)

```java
private static final Set<String> PROBABILITY_SCORED_TYPES = Set.of(
    "MATCH_RESULT", "BTTS", "DOUBLE_CHANCE", "RESULT_BTTS", "TOP_VS_BOTTOM",
    "OVER_05_GOALS", "OVER_15_GOALS", "OVER_25_GOALS", "PLAYER_TO_SCORE", 
    "PLAYER_TO_ASSIST", "DRAW", "FIRST_HALF_GOALS", "SECOND_HALF_GOALS", 
    "VALUE_BET", "OVER_GOALS", "UNDER_GOALS", "CLEAN_SHEET"
);
```

### Recommended Calibration Improvements

1. **Implement Platt Scaling** for probability calibration:
```java
// Logistic regression on validation set
double calibratedProb = 1.0 / (1.0 + Math.exp(-(a * rawScore + b)));
```

2. **Add Temperature Scaling** as a simpler alternative:
```java
// Single parameter learned from validation data
double temperature = 1.1; // Example: slight cooling
double calibratedProb = rawProb / (rawProb + (1 - rawProb) * Math.pow(1 - rawProb, temperature - 1));
```

3. **Track Rolling Calibration** by time window:
   - 7-day rolling calibration gap
   - 30-day rolling calibration gap
   - Alert when gap exceeds ±5 points consistently

---

## Architectural Improvements

### 1. Ensemble Scoring

Current engines work independently. Consider:

```java
public interface EnsembleContributor {
    Optional<EnsembleSignal> contributeToFixture(FixtureContext context);
}

record EnsembleSignal(
    String market,           // e.g., "Over 2.5 Goals"
    double probability,      // Our estimate
    double confidence,       // How certain we are
    String rationale         // Why
) {}
```

Benefits:
- Multiple engines can vote on overlapping markets
- Disagreement between engines flags uncertainty
- Agreement strengthens confidence

### 2. Feature Importance Tracking

Add logging to track which factors drive predictions:

```java
record FeatureContribution(
    String featureName,
    double featureValue,
    double contribution,     // How much it moved the score
    double weight           // Weight used
) {}
```

This enables:
- Post-hoc analysis of what's working
- Automated detection of features that stopped adding value
- A/B testing of new features

### 3. Confidence Calibration

Current system uses fixed thresholds. Consider adaptive thresholds:

```java
public class AdaptiveThresholdService {
    // Learn thresholds from recent performance
    public double getStrongThreshold(RecommendationType type) {
        double recent7dHitRate = getRecentHitRate(type, 7);
        double recent30dHitRate = getRecentHitRate(type, 30);
        
        // If recent performance drops, raise threshold
        if (recent7dHitRate < recent30dHitRate - 5.0) {
            return baseThreshold + 3.0; // Temporarily stricter
        }
        return baseThreshold;
    }
}
```

### 4. Real-time Performance Dashboard Metrics

Extend `ResultsPerformanceService.BucketStats` with:

```java
record EnhancedStats(
    // Existing fields...
    
    // New metrics
    double sharpeRatio,           // Risk-adjusted return
    double maxDrawdown,           // Worst losing streak
    double expectedValuePerPick,  // Average EV
    double bettingLeverage,       // Kelly vs actual stakes
    int daysSinceLastRecalibration
) {}
```

---

## Priority Implementation Roadmap

### High Priority (Next Sprint)

1. **Restore Away Tips** with stricter thresholds
   - Files: `MatchResultRecommendationEngine.java`
   - Effort: Medium
   - Risk: Low if thresholds are conservative

2. **Add CLV Tracking** for Value Bet validation
   - Files: `ValueBetRecommendationEngine.java`, new `CLVService.java`
   - Effort: Medium
   - Risk: Low (read-only metrics)

3. **Implement 7-day Rolling Calibration Alerts**
   - Files: `ResultsPerformanceService.java`
   - Effort: Low
   - Risk: Low

### Medium Priority (Next Month)

4. **League-specific Priors** for Draw and Clean Sheet
   - Collect league-level base rates
   - Apply Bayesian shrinkage toward league prior

5. **Player Form Tracking** for Props
   - Last 3-5 game moving averages
   - Momentum detection (hot/cold streaks)

6. **Ensemble Scoring** pilot for Goals markets
   - Combine Over 1.5/2.5/3.5 signals
   - Add market agreement confidence

### Lower Priority (Future)

7. **Machine Learning Calibration** (Platt/Temperature scaling)
8. **Weather/Pitch Data Integration**
9. **Fixture Congestion Signals**
10. **Keeper Save Rate Integration**

---

## Testing Recommendations

### Unit Test Coverage Gaps

Based on code review, add tests for:

1. **Edge cases in Poisson calculations:**
```java
@Test
void poissonDrawProbability_withZeroExpectedGoals_returnsHighDraw() {
    // Both teams at 0.0 expected goals = certainty of 0-0
    assertThat(poissonDrawProbability(0.0, 0.0)).isCloseTo(100.0, within(0.1));
}
```

2. **Shrinkage at extreme samples:**
```java
@Test  
void shrink_withVerySmallSample_movesTowardPrior() {
    double observed = 100.0; // "100% success"
    int sample = 1;
    double prior = 50.0;
    double shrunk = shrink(observed, sample, prior);
    // With 1 observation + 6 pseudo-matches, should be near prior
    assertThat(shrunk).isBetween(50.0, 60.0);
}
```

3. **Threshold boundary conditions:**
```java
@Test
void determineConfidence_atExactThreshold_returnsHigherTier() {
    // When score == THRESHOLD_STRONG exactly
    assertThat(determineConfidence(72.0)).isEqualTo(ConfidenceLevel.STRONG);
}
```

### Integration Test Scenarios

Add scenarios for:
- Full fixture analysis with all engines
- Day-over-day calibration drift detection
- Elite picks selection with ties
- Settlement edge cases (postponed matches, walkovers)

---

## Metrics to Monitor

### Daily Dashboard

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Overall 7d hit rate | 55%+ | < 50% |
| Strong pick hit rate | 65%+ | < 58% |
| Elite pick hit rate | 60%+ | < 52% |
| ROI (flat stakes) | > 0% | < -5% |
| Calibration gap | ±3 pts | > ±8 pts |

### Weekly Review

- Per-engine hit rates vs claimed scores
- Calibration band gaps by type
- Volume of picks by confidence level
- Market coverage (% of fixtures with recommendations)

---

## Conclusion

The recommendation engine system is architecturally sound with good use of:
- Poisson probability estimation
- Bayesian shrinkage for small samples  
- Weight renormalization for missing data
- Realistic probability ceilings

The main opportunities for improvement are:
1. Restoring paused markets with appropriate calibration
2. Implementing ensemble approaches for related markets
3. Adding automated calibration drift detection
4. Incorporating additional data signals (form momentum, H2H, weather)

The priority should be stabilizing existing engines through better calibration tracking before adding new complexity.
