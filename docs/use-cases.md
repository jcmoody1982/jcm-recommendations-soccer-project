# Use Cases

Living document tracking the use cases for the soccer recommendations system.

## Template

When adding a new use case, copy this template:

```markdown
### UC-XXX: [Name]

**Goal:** [What recommendation or insight does this provide?]

**User Story:** As a [user type], I want to [action] so that [benefit].

**Data Required:**
- [Entity 1]: [key fields needed]
- [Entity 2]: [key fields needed]

**API Source(s):** [Where does the data come from?]

**Status:** Draft | Reviewed | In Progress | Done
```

---

## Use Cases

### Feed Handling / Data Gathering

_Use cases related to ingesting, storing, and maintaining data from external APIs._

---

#### UC-001: Upcoming Fixtures for Supported Leagues

**Goal:** Display a list of upcoming fixtures for all supported leagues, refreshed daily.

**User Story:** As a user, I want to see the next upcoming fixtures for leagues I care about so that I can plan what matches to watch.

**Data Required:**
- **League**: name, country, image
- **Season**: id, year, country (use only the **latest/current season** per league)
- **Fixture (basic)**: id, league/season, home team, away team, match date/time, venue, status, gameWeek
- **Fixture (referee)**: refereeID, referee
- **Fixture (pre-match potentials)**: btts_potential, o15_potential, o25_potential, o35_potential, o45_potential, o05HT_potential, o15HT_potential, u15_potential, avg_potential, corners_potential, corners_o85_potential, corners_o95_potential, corners_o105_potential, cards_potential, offsides_potential
- **Fixture (betting odds)**: odds_ft_1, odds_ft_x, odds_ft_2, odds_ft_over05 to odds_ft_over45, odds_ft_under05 to odds_ft_under45, odds_btts_yes, odds_btts_no
- **Team**: id, name

**API Source:** football-data-api.com

**Leagues:** 42 leagues returned from `/league-list?chosen_leagues_only=true`. Use only the **last season entry** from each league's `season` array (the current season). Example leagues:
- England: Premier League, Championship, EFL League One, EFL League Two
- Spain: La Liga, Segunda División
- Germany: Bundesliga, 2. Bundesliga
- Italy: Serie A, Serie B
- France: Ligue 1, Ligue 2
- Portugal: Liga NOS, LigaPro
- Netherlands: Eredivisie
- Belgium: Pro League
- Scotland: Premiership, League One
- Turkey: Süper Lig
- Brazil: Serie A, Serie B
- USA: MLS
- Mexico: Liga MX
- And more (42 total)

**Behavior:**
- Fetch league list from API (or use cached version)
- For each league, extract the **last entry** from the `season` array (= current season ID)
- Pull upcoming fixtures for the next **7 days** using the current season ID
- Refresh data on a **daily schedule**
- Store fixtures locally so the app can serve them without hitting the API on every request

**Status:** Reviewed

**Next Steps:**
- [ ] Define domain entities (League, Fixture, Team)
- [ ] Implement persistence layer
- [ ] Build API client to fetch data
- [ ] Implement daily refresh scheduler

---

#### UC-002: Maintain Enriched Team List with Statistics

**Goal:** Build and maintain a distinct list of team domain objects with full season statistics.

**User Story:** As the system, I want to maintain a canonical list of teams with their stats so that team data is normalized and reusable across features.

**Data Required:**
- **Team (basic)**: id, name, country, stadium_name, leagueId/seasonId
- **Team (season stats)**: All stats from `/league-teams?include=stats`

**API Source:** 
- `/league-teams?season_id=XXX&include=stats` — full team data with season statistics

**Key Team Fields:**
| Field | Description |
|-------|-------------|
| `id` | Team ID |
| `name` / `cleanName` | Team name |
| `country` | Country |
| `image` | Team logo URL |
| `stadium_name` | Home stadium |
| `matchesPlayed` | Games played |
| `points` | League points |
| `position` | League position |
| `seasonWins_home/away` | Wins (home/away split) |
| `seasonDraws_home/away` | Draws (home/away split) |
| `seasonLosses_home/away` | Losses (home/away split) |
| `seasonGoals` / `seasonGoals_home/away` | Goals scored |
| `seasonConceded_home/away` | Goals conceded |
| `seasonGoalDifference` | Goal difference |
| Plus all other season stats | PPG, BTTS%, Over/Under, form, etc. |

**Behavior:**
- For each league's current season, call `/league-teams?season_id=XXX&include=stats`
- Store team basic info + all season statistics
- Link teams to their league/season
- Upsert by team ID (update stats on each refresh)
- Refresh alongside fixtures (daily schedule)

**Dependencies:** UC-001 (need season IDs from league list)

**Status:** Reviewed

---

#### UC-003: Maintain Referee List with Statistics

**Goal:** Build and maintain a list of referees with their officiating statistics for each league/season.

**User Story:** As a user, I want to see referee statistics so that I can understand tendencies (cards, goals, penalties) for upcoming fixtures.

**Data Required:**
- **Referee (basic)**: id, full_name, first_name, last_name, known_as
- **Referee (match outcomes)**: appearances_overall, wins_home, wins_away, draws_overall
- **Referee (outcome percentages)**: wins_per_home, wins_per_away, draws_per
- **Referee (goals & BTTS)**: goals_overall, goals_home, goals_away, goals_per_match_overall, goals_per_match_home, goals_per_match_away, btts_overall, btts_percentage
- **Referee (penalties)**: penalties_given_overall, penalties_given_home, penalties_given_away, penalties_given_per_match_overall, penalties_given_per_match_home, penalties_given_per_match_away, penalties_given_percentage_overall, penalties_given_percentage_home, penalties_given_percentage_away
- **Referee (cards)**: cards_overall, cards_home, cards_away, cards_per_match_overall, cards_per_match_home, cards_per_match_away, yellow_cards_overall, red_cards_overall, over05_cards_overall to over65_cards_overall, over05_cards_percentage_overall to over65_cards_percentage_overall, min_per_card_overall

**API Source:**
- `/league-referees?season_id=XXX` — referees with statistics for a season

**Behavior:**
- For each league's current season, call `/league-referees?season_id=XXX`
- Store referee basic info + all statistics
- Link referees to their league/season
- Upsert by referee ID (update stats on each refresh)
- Refresh alongside fixtures (daily schedule)
- Link to fixtures via `refereeID` field from UC-001

**Dependencies:** UC-001 (need season IDs from league list; fixtures reference refereeID)

**Status:** Reviewed

---

#### UC-004: Team Recent Form Stats (Last 5 Matches)

**Goal:** Track recent form statistics for teams to supplement season-level data with current momentum.

**User Story:** As a user, I want to see a team's recent form (last 5 matches) so that I can understand their current momentum and make better predictions.

**Data Required:**
- **Team (identity)**: id, name, competition_id
- **Results & Points**: seasonWinsNum, seasonDrawsNum, seasonLossesNum, seasonPPG, table_position, performance_rank (all with overall/home/away splits)
- **Goals**: seasonGoals, seasonConceded, seasonGoalsTotal, seasonGoalDifference, scoredAVG, concededAVG (all with overall/home/away splits)
- **BTTS**: seasonBTTS, seasonBTTSPercentage, seasonBTTSHT, seasonBTTSPercentageHT (all with overall/home/away splits)
- **Over/Under Goals**: seasonOver05Num to seasonOver55Num, seasonOver05Percentage to seasonOver55Percentage, seasonUnder05Num to seasonUnder55Num (all with overall/home/away splits)
- **Corners**: cornersTotal, cornersTotalAVG, cornersAgainst, cornersAgainstAVG, cornersHighest, cornersLowest (all with overall/home/away splits)
- **Cards**: cardsTotal, cardsAVG, cards_for, cards_against, cards_for_avg, cards_against_avg, fh_cards_total, 2h_cards_total (all with overall/home/away splits)
- **Other**: foulsTotal, foulsAVG, cleanSheets, failedToScore (all with overall/home/away splits)

**API Source:**
- `/lastx?team_id=XXX` — returns last 5, 6, and 10 match stats in one call (focus on last 5)

**Behavior:**
- For teams appearing in upcoming fixtures (UC-001), fetch their recent form
- Store last 5 match statistics
- Link to team entity via team ID
- Refresh alongside fixtures (daily schedule)
- Use to enhance fixture predictions alongside season stats (UC-002)

**Dependencies:** 
- UC-001 (identifies teams in upcoming fixtures)
- UC-002 (team IDs for lookup)

**API Call Strategy:**
- Fetch on-demand for teams in upcoming fixtures only
- ~40 teams per day (avg 2 teams × ~20 fixtures across leagues with games that day)
- Avoids 840+ calls for all teams

**Status:** Reviewed

---

### Recommendations & Predictions

_Use cases for generating insights, recommendations, and predictions based on collected data._

---

#### UC-005: BTTS Recommendations

**Goal:** Identify fixtures with high likelihood of both teams scoring.

**User Story:** As a user, I want to see which upcoming matches are most likely to have both teams score so I can make informed BTTS bets.

**Data Required:**
- Team BTTS percentage (season + recent form), venue-specific
- Goals scored/conceded averages at venue (from W+D+L match counts)
- Failed to score percentage (venue-aware when sample ≥ 3)
- BTTS potential from API (when present)
- xG for/against averages (when available)

**Implementation:** `BttsRecommendationEngine.java`

**Logic:**

*Base weights (preferred; renormalized when signals are missing):*
```
BTTS Score = weighted average of available signals (all rates shrunk, see below):
  - Home team BTTS % (season, home)     × 0.15
  - Away team BTTS % (season, away)     × 0.15
  - Home team BTTS % (form, shrunk)     × 0.20
  - Away team BTTS % (form, shrunk)     × 0.20
  - P(both teams score), multiplicative × 0.20
  - API btts_potential                  × 0.10
```

**Sample-size shrinkage (P3):**
```
Every observed rate is pulled toward a league prior by 6 pseudo-matches:
  shrunk = (observed × n + prior × 6) / (n + 6)

  prior = 50%  for BTTS rates
  prior = 74%  for "this team scores at least once" rates

Effect: a 5-from-5 run reads as "likely", not as a literal 100%.
```

**Both-teams-score signal (P3):**
```
BTTS is a conjunction, so the two one-sided scoring rates are combined
multiplicatively rather than averaged as two independent signals:

  P(both) = shrunk P(home scores) × shrunk P(away scores)

Averaging two ~100% one-sided rates previously overstated a two-sided event.
```

**Missing data (P2):**
```
Null BTTS % or missing API potential are OMITTED (not defaulted to 50).
Remaining signal weights are renormalized to sum to 1.0.
```

**Form sample dampening (P1):**
```
Venue form sample = wins + draws + losses at venue.
  - sample < 3: omit form signal (weights renormalize)
  - sample ≥ 3: shrink form % toward season % (or the 50% prior) by sample size
  - If form % is present but W/D/L counts are missing: assume sample of 3
```

**Venue match counts (P0):**
```
Goals / conceded averages use W+D+L at venue — never matchesPlayed / 2.
```

**Filters (P1 — venue-aware):**
```
When venue matches ≥ 3:
  - Home scored % / FTS % from home venue games
  - Away scored % / FTS % from away venue games
When venue matches < 3: fall back to overall season rates.

Both teams must have scored in ≥ 50% of (venue/overall) matches.
Neither team's FTS rate may exceed 40%.
```

**Goals Context Boost (graded, max +5%):**
```
Home strength vs 1.5 goals/home game; away strength vs 1.0 goals/away.
Strength ramps from 0 at (threshold − 0.25) to 1 at (threshold + 0.5).
Boost = 5% × √(homeStrength × awayStrength)
```

**Defensive Leakiness Boost (graded, max +4%):**
```
Home vs 1.2 conceded/home; away vs 1.0 conceded/away.
Same graded ramp; Boost = 4% × √(homeStrength × awayStrength)
```

**xG Matchup Boost (graded, max +3%):**
```
When xG for available for both sides:
  homeAttack = (home xG for home + away xGA away) / 2   (fallback: home xG for)
  awayAttack = (away xG for away + home xGA home) / 2   (fallback: away xG for)
  combined = homeAttack + awayAttack
  Boost ramps 0→3% between combined 2.0 and 3.0
```

**Combined boost cap (P2):**
```
appliedBoost = min(8.0, goalsBoost + leakyBoost + xgBoost)
```

**Realistic ceiling (P3):**
```
Scores above 75% are compressed asymptotically toward a 85% ceiling:
  score = 75 + 10 × (1 − e^−((raw − 75) / 10))

Ranking is preserved and the ceiling is never reached, so BTTS is never
presented as a certainty. Previously the total was clamped at 100, which
masked overflow instead of preventing it.
```

**Thresholds** (rebased onto the shrunk, ceiling-capped scale):
- **Strong:** BTTS Score ≥ 72%
- **Moderate:** BTTS Score 62-71%
- **Weak:** BTTS Score < 62% (filtered out)

**Output:**
- Ranked list of fixtures by BTTS score
- Include: fixture details, both team stats, confidence level
- Factors tracked:
  - `filtersVenueAware`, `homeVenueMatches`, `awayVenueMatches`
  - `homeVenueScoredPct` / `awayVenueScoredPct`
  - `formDataAvailable`, `homeFormSampleSize`, `awayFormSampleSize`
  - `missingDataRenormalized`, `signalsUsed`
  - `homeGoalsAvgHome` / `awayGoalsAvgAway`
  - `homeConcededAvgHome` / `awayConcededAvgAway`
  - `goalsBoostApplied` / `goalsBoostAmount`
  - `leakyDefenseBoostApplied` / `leakyDefenseBoostAmount`
  - `xgDataAvailable`, `combinedXgMatchup` (or `combinedXg`)
  - `xgBoostApplied` / `xgBoostAmount`
  - `baseScore`, `appliedBoost`, `boostCapped`, `maxCombinedBoost`
  - `calculatedScore`
  - `bothTeamsScoreEstimate`, `shrinkageApplied`
  - `realisticCeiling`, `ceilingApplied`

**Status:** `Implemented`

---

#### UC-006: Over Goals Recommendations

**Goal:** Identify fixtures likely to be high-scoring (Over 2.5, Over 3.5).

**User Story:** As a user, I want to see which matches are likely to have many goals so I can bet on over goals markets.

**Data Required:**
- Goals scored/conceded averages (season + form)
- Over 2.5/3.5 percentages
- o25_potential, o35_potential from API
- xG for/against averages (when available)

**Logic:**

*Base Weights (when form data IS available - 11 factors, total = 1.0):*
```
Over Goals Score = weighted average of:
  - Home team goals scored avg (season)      × 0.08
  - Away team goals scored avg (season)      × 0.08
  - Home team goals conceded avg (season)    × 0.08
  - Away team goals conceded avg (season)    × 0.08
  - Home team goals scored avg (form)        × 0.12
  - Away team goals scored avg (form)        × 0.12
  - Home team goals conceded avg (form)      × 0.08
  - Away team goals conceded avg (form)      × 0.08
  - Home team Over 2.5 % (season)            × 0.08
  - Away team Over 2.5 % (season)            × 0.08
  - API o25_potential                        × 0.12
```

*Redistributed Weights (when form data is NOT available - 7 factors, total = 1.0):*
```
Over Goals Score = weighted average of:
  - Home team goals scored avg (season)      × 0.15
  - Away team goals scored avg (season)      × 0.15
  - Home team goals conceded avg (season)    × 0.12
  - Away team goals conceded avg (season)    × 0.12
  - Home team Over 2.5 % (season)            × 0.12
  - Away team Over 2.5 % (season)            × 0.12
  - API o25_potential                        × 0.22
```

**High-Scoring Context Boost:**
```
When combined goals average ≥ 3.0, add +5% to final score.
Combined avg = (home scored + away scored + home conceded + away conceded) / 2
```

**xG Boost:**
```
When combined xG (home xG + away xG) ≥ 2.8, add +4% to final score.
xG data is blended into expected goals calculation (60% actual, 40% xG).
```

**Expected Goals Calculation:**
```
Actual Expected = (Home scored + Away scored + Home conceded + Away conceded) / 2

If xG data available:
  xG Expected = (Home xG for + Away xG for + Home xG against + Away xG against) / 2
  Final Expected = (Actual Expected × 0.6) + (xG Expected × 0.4)
```

**Thresholds:**
- **Strong:** Score ≥ 80%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65% (filtered out)

**Market Selection:**
- **Over 3.5 Goals:** Expected goals ≥ 3.5 AND score ≥ 80% AND avg Over 3.5% ≥ 40%
- **Over 2.5 Goals:** Otherwise

**Additional Filters:**
- Expected goals ≥ 2.5 per match

**Output:**
- Ranked list of fixtures by over goals score
- Include: expected goals, team averages, confidence level
- Factors tracked:
  - `formDataAvailable` - whether form data was used
  - `expectedGoals` - calculated expected goals for the match
  - `homeGoalsScoredAvg` / `awayGoalsScoredAvg` - season scoring rates
  - `homeGoalsConcededAvg` / `awayGoalsConcededAvg` - season conceding rates
  - `homeOver25Pct` / `awayOver25Pct` - Over 2.5 percentages
  - `homeOver35Pct` / `awayOver35Pct` - Over 3.5 percentages
  - `combinedGoalsAvg` - combined average for boost calculation
  - `highScoringBoostApplied` / `highScoringBoostAmount` - high-scoring boost
  - `xgDataAvailable` - whether xG data is available
  - `homeXgForAvgHome` / `awayXgForAvgAway` - expected goals scored
  - `homeXgAgainstAvgHome` / `awayXgAgainstAvgAway` - expected goals conceded
  - `xgBoostApplied` / `xgBoostAmount` / `combinedXg` - xG boost details
  - Form data (when available): `homeScoredFormAvg`, `awayScoredFormAvg`, `homeConcededFormAvg`, `awayConcededFormAvg`

**Related:** Dedicated **Over 1.5 Goals** (UC-038) and **Over 2.5 Goals** (UC-039) boards sit after Top vs Bottom on the Recommendations page. This engine remains the high-scoring selector that may step up to Over 3.5.

**Status:** `Implemented`

---

#### UC-007: Under Goals Recommendations

**Goal:** Identify fixtures likely to be low-scoring (Under 2.5, Under 1.5).

**User Story:** As a user, I want to see which matches are likely to have few goals so I can bet on under goals markets.

**Data Required:**
- Goals scored/conceded averages (season + form)
- Under 2.5/1.5 percentages (derived from Over percentages)
- Clean sheet percentages
- Failed to score percentages
- xG for/against averages (when available)

**Logic:**

*Base Weights (when form data IS available - 13 factors, total = 1.0):*
```
Under Goals Score = weighted average of:
  - Home team goals scored avg (season) inverse      × 0.07
  - Away team goals scored avg (season) inverse      × 0.07
  - Home team goals conceded avg (season) inverse    × 0.07
  - Away team goals conceded avg (season) inverse    × 0.07
  - Home team goals scored avg (form) inverse        × 0.10
  - Away team goals scored avg (form) inverse        × 0.10
  - Home team goals conceded avg (form) inverse      × 0.06
  - Away team goals conceded avg (form) inverse      × 0.06
  - Home team clean sheet % (season)                 × 0.08
  - Away team clean sheet % (season)                 × 0.08
  - Home team failed to score % (season)             × 0.06
  - Away team failed to score % (season)             × 0.06
  - API u15_potential                                × 0.12
```

*Redistributed Weights (when form data is NOT available - 9 factors, total = 1.0):*
```
Under Goals Score = weighted average of:
  - Home team goals scored avg (season) inverse      × 0.12
  - Away team goals scored avg (season) inverse      × 0.12
  - Home team goals conceded avg (season) inverse    × 0.10
  - Away team goals conceded avg (season) inverse    × 0.10
  - Home team clean sheet % (season)                 × 0.12
  - Away team clean sheet % (season)                 × 0.12
  - Home team failed to score % (season)             × 0.10
  - Away team failed to score % (season)             × 0.10
  - API u15_potential                                × 0.22
```

**Low-Scoring Context Boost:**
```
When combined goals average ≤ 2.0, add +5% to final score.
Combined avg = (home scored + away scored + home conceded + away conceded) / 2
```

**Defensive Strength Boost:**
```
When both teams have clean sheet % ≥ 30%, add +4% to final score.
Rewards matchups between defensively solid teams.
```

**xG Boost (Low Expected Goals):**
```
When combined xG (home xG + away xG) ≤ 2.2, add +4% to final score.
xG data is blended into expected goals calculation (60% actual, 40% xG).
```

**Expected Goals Calculation:**
```
Actual Expected = (Home scored + Away scored + Home conceded + Away conceded) / 2

If xG data available:
  xG Expected = (Home xG for + Away xG for + Home xG against + Away xG against) / 2
  Final Expected = (Actual Expected × 0.6) + (xG Expected × 0.4)
```

**Thresholds:**
- **Strong:** Score ≥ 80%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65% (filtered out)

**Market Selection:**
- **Under 1.5 Goals:** Expected goals ≤ 1.5 AND score ≥ 80% AND avg Under 1.5% ≥ 25%
- **Under 2.5 Goals:** Otherwise

**Additional Filters:**
- Expected goals ≤ 2.5 per match

**Output:**
- Ranked list of fixtures by under goals score
- Include: expected goals, defensive stats, confidence level
- Factors tracked:
  - `formDataAvailable` - whether form data was used
  - `expectedGoals` - calculated expected goals for the match
  - `homeGoalsScoredAvg` / `awayGoalsScoredAvg` - season scoring rates
  - `homeGoalsConcededAvg` / `awayGoalsConcededAvg` - season conceding rates
  - `combinedGoalsAvg` - combined average for boost calculation
  - `homeCleanSheetPct` / `awayCleanSheetPct` - defensive strength
  - `homeFailedToScorePct` / `awayFailedToScorePct` - scoring struggles
  - `homeUnder15Pct` / `awayUnder15Pct` - Under 1.5 percentages
  - `homeUnder25Pct` / `awayUnder25Pct` - Under 2.5 percentages
  - `lowScoringBoostApplied` / `lowScoringBoostAmount` - low-scoring boost
  - `defensiveStrengthBoostApplied` / `defensiveStrengthBoostAmount` - defensive boost
  - `xgDataAvailable` - whether xG data is available
  - `homeXgForAvgHome` / `awayXgForAvgAway` - expected goals scored
  - `homeXgAgainstAvgHome` / `awayXgAgainstAvgAway` - expected goals conceded
  - `xgBoostApplied` / `xgBoostAmount` / `combinedXg` - xG boost details
  - Form data (when available): `homeScoredFormAvg`, `awayScoredFormAvg`, `homeConcededFormAvg`, `awayConcededFormAvg`

**Status:** `Implemented`

---

#### UC-008: Booking Points Recommendations

**Goal:** Predict total booking points for a fixture (Yellow=10, Red=25).

**User Story:** As a user, I want to see expected booking points for matches so I can bet on cards markets.

**Data Required:**
- Team cards averages (season + form) — card counts, converted ×10 to points
- Referee cards per match stats (reliability-weighted by appearances)
- Referee over 3.5 cards percentage
- Referee yellow/red card breakdown (red-card risk)
- API `cards_potential` as **expected card count** (not 0–100)

**Implementation:** `BookingPointsRecommendationEngine.java`

**Logic:**

*Base model (preferred weights; renormalized when signals missing):*
```
Expected Booking Points = weighted average of match-level signals (all in points):
  - (Home cards avg season + Away cards avg season) × 10     × 0.28
  - (Home cards avg form + Away cards avg form) × 10         × 0.20  (when form present)
  - Referee cards/match × 10 × reliability                     × 0.20
  - Referee O3.5% → soft points × reliability                  × 0.08
  - Red card risk (ref rate × 25) × reliability                × 0.06
  - API cards_potential (match card count) × 10                × 0.18
+ Match intensity points (additive, not multiplier)
+ Graded boosts (capped)
```

Team season/form signals are **summed across both sides** before ×10 so the model estimates
match total booking points, not the average of per-team contributions.

**P0 — `cards_potential` scale:**
```
FootyStats cards_potential = expected card COUNT for the match (~3–7).
Convert to points: count × 10.
Never default missing potential to 40 — omit signal and renormalize.
```

**P0 — Selectivity (edge vs line):**
```
Only tip when |expected − line| ≥ 8:
  - Over 50 if expected ≥ 58
  - Over 40 if expected ≥ 48
  - Under 30 if expected ≤ 22
  - Under 40 if expected ≤ 32
Pick the qualifying market with the largest edge.
Mid-range fixtures (no edge) → no recommendation.
```

**P1 — Units + boosts + intensity:**
```
Score is expected booking points (card-count ×10 + red risk).
High-cards boost: graded, max +3 (both teams ≥ ~2.0 cards/game).
Referee strictness boost: graded, max +3 (O3.5% ≥ ~60%).
Combined boost cap: +5.
Match intensity: +4 pts if position gap ≤ 3; +2 if gap 4–6; else 0.
```

**P1 — Push on exact line (settlement):**
```
When actual booking points == market line → VOID (push), not LOSS.
```

**P2 — Referee:**
```
Referee reliability by appearances: ≥10 → 1.0, ≥5 → 0.8, else 0.5.
Reliability scales referee signal weights.
Without referee data: confidence capped at MODERATE (never STRONG).
STRONG requires edge ≥ 12 AND referee present.
MODERATE requires edge ≥ 8.
```

**Thresholds:**
- **Strong:** edge ≥ 12 and referee present
- **Moderate:** edge ≥ 8 (including no-referee tips)
- **No tip:** edge < 8 vs all lines

**Output / factors:**
- `expectedBookingPoints`, `basePoints`, `marketLine`, `marketEdge`, `minEdgeRequired`
- `cardsPotentialIsCardCount`, `apiCardsPotential`, `apiCardsPotentialAsPoints`
- `missingDataRenormalized`, `signalsUsed`
- `refereeDataAvailable`, `refereeReliability`, `refereeRequiredForStrong`
- `matchIntensityPoints` (not a multiplier)
- `highCardsBoostApplied` / amount, `refereeStrictnessBoostApplied` / amount
- `appliedBoost`, `boostCapped`, `maxCombinedBoost`

**Status:** `Implemented`

---

#### UC-009: Value Bet Recommendations

**Goal:** Flag fixtures where calculated probability differs significantly from bookmaker odds.

**User Story:** As a user, I want to find bets where the odds offer value compared to statistical probability.

**Data Required:**
- Calculated probabilities from other use cases (UC-005, UC-006, UC-007)
- Bookmaker odds from API (all markets)
- Team season statistics with xG data
- Recent form data for enhanced match result probabilities

**Markets Analyzed:**
| Market | Odds Field | Probability Source |
|--------|------------|-------------------|
| BTTS Yes | `oddsBttsYes` | UC-005 BTTS Engine score |
| BTTS No | `oddsBttsNo` | 100 - UC-005 score (inverse) |
| Over 1.5 Goals | `oddsFtOver15` | UC-006 adjusted (+15%) |
| Over 2.5 Goals | `oddsFtOver25` | UC-006 Over Goals Engine score |
| Over 3.5 Goals | `oddsFtOver35` | UC-006 adjusted (-25%) |
| Under 1.5 Goals | `oddsFtUnder15` | UC-007 adjusted (-15%) |
| Under 2.5 Goals | `oddsFtUnder25` | UC-007 Under Goals Engine score |
| Under 3.5 Goals | `oddsFtUnder35` | UC-007 adjusted (+25%) |
| Home Win | `oddsFt1` | Home-only enhanced probability (Away/Draw value paused) |
| ~~Draw~~ | ~~`oddsFtX`~~ | Paused |
| ~~Away Win~~ | ~~`oddsFt2`~~ | Paused |

**Logic:**
```
For each market:

1. Calculate implied probability from bookmaker odds:
   Implied % = 1 / decimal_odds × 100

2. Get our calculated probability from relevant use case:
   - BTTS Yes/No: UC-005 score (or inverse)
   - Over Goals: UC-006 score (adjusted for market)
   - Under Goals: UC-007 score (adjusted for market)
   - Match Result: Enhanced calculation using form + xG

3. Calculate value:
   Value % = Our Probability - Implied Probability

4. Calculate expected value (EV):
   EV = (Our Probability × (odds - 1)) - (1 - Our Probability)

5. Apply source confidence weight:
   Weighted EV = EV × Source Weight
   - Strong source: × 1.0
   - Moderate source: × 0.8
   - Weak source: × 0.5

6. Calculate Kelly Criterion stake:
   Kelly = ((odds - 1) × probability - (1 - probability)) / (odds - 1)
   Suggested Stake = Kelly × 0.25 (quarter Kelly for safety)
   Maximum stake capped at 10% of bankroll
```

**Match Result Enhanced Probability:**
```
1. Base probability from season win rates (home/away specific)
2. Blend with recent form PPG (60% season, 40% form)
3. Incorporate xG ratio if available (70% base, 30% xG)
4. Normalize to ensure probabilities sum to 1
5. Draw constrained to 15-35% range
```

**Thresholds (recalibrated Aug 2026):**
- **Strong Value:** Value % ≥ 20% AND EV ≥ 0.12 AND odds ≤ 2.50 AND source engine **Strong**
- **Moderate Value:** Value % ≥ 15% AND EV ≥ 0.08 AND odds ≤ 2.50
- **No Value:** Below thresholds OR odds outside range
- **Odds Range:** 1.50 to 2.50 (drops the 2.50–3.00 band with poor snapshot hit rates)
- **1X2 scope:** Home Win value only; Away Win and Draw paused (aligned with Match Result recalibration)

**Best Opportunity Selection:**
- All qualifying opportunities ranked by weighted EV
- Best opportunity returned as primary recommendation
- All opportunities tracked in factors for transparency

**Output:** market, probabilities, odds, EV, Kelly stake; factors include `awayWinValuePaused`, `drawValuePaused`, and opportunity breakdowns.

**Status:** `Implemented` (recalibrated)

---

#### UC-010: Winning Form Mismatch Recommendations

**Goal:** Identify teams whose recent form is significantly better than their season average suggests.

**User Story:** As a user, I want to spot teams on hot streaks that may be undervalued by the market.

**Data Required:**
- Season PPG, goals scored avg, goals conceded avg, win percentage, clean sheet %
- Recent form (last 5) PPG, goals scored avg, goals conceded avg, win percentage, clean sheet %
- Home/away splits for both season and recent form
- xG data for regression risk assessment
- Upcoming fixture location (home/away)

**Logic:**
```
Winning Mismatch Score = weighted average of deltas:
  - PPG delta: (Last 5 PPG - Season PPG) / Season PPG           × 0.25
  - Goals delta: (Last 5 goals avg - Season goals avg)          × 0.20
  - Conceded delta: (Season conceded - Last 5 conceded) / Season × 0.15  (inverted - lower = better)
  - Wins delta: (Last 5 win % - Season win %)                   × 0.20
  - Clean sheets delta: (Last 5 CS % - Season CS %)             × 0.20
```

**Home/Away Context Weighting:**
```
If team is playing at home (their strength venue):
  Final Score = Base Score × 1.25
```

**Trend Bonuses:**
```
Scoring Trend Up: +3% if home scoring > overall scoring by 0.3 goals
Defensive Trend Up: +2% if home conceding < overall conceding by 0.3 goals
Winning Streak: +5% if team has won 3+ of last 5 matches
```

**xG Regression Risk:**
```
Flag as regression risk if:
  Actual goals scored - xG for avg > 0.5 goals per game
  (Team scoring significantly above expected - likely to regress)
```

**Thresholds:**
- **Strong Mismatch:** Score ≥ 25% improvement
- **Moderate Mismatch:** Score 15-24% improvement
- **No Mismatch:** Score < 15% improvement

**Output:**
- Single best mismatch recommendation per fixture
- Positive score = Winning Form Mismatch (hot streak)
- Include: all delta percentages, trend indicators, streak status
- Flag: "Hot streak - potentially undervalued"
- Factors tracked:
  - `team` / `isHomeTeam` / `playingAtStrength` - team context
  - `mismatchScore` - overall mismatch percentage
  - `ppgDelta` / `goalsDelta` / `concededDelta` / `winsDelta` / `cleanSheetDelta` - individual deltas
  - `hasWinningStreak` / `hasLosingStreak` - streak indicators
  - `scoringTrendUp` / `defensiveTrendUp` - momentum indicators
  - `xgRegressionRisk` - regression warning flag
  - `homeAwayContextMultiplier` - context weighting applied
  - `positiveMomentumIndicators` - count of positive signals
  - `riskFlags` - list of risk warnings

**Status:** `Implemented`

---

#### UC-011: Losing Form Mismatch Recommendations

**Goal:** Identify teams whose recent form is significantly worse than their season average suggests.

**User Story:** As a user, I want to spot teams on cold streaks that may be overvalued by the market.

**Data Required:**
- Season PPG, goals scored avg, goals conceded avg, win percentage, clean sheet %
- Recent form (last 5) PPG, goals scored avg, goals conceded avg, win percentage, clean sheet %
- Home/away splits for both season and recent form
- Upcoming fixture location (home/away)

**Logic:**
```
Handled by FormMismatchRecommendationEngine (same as UC-010).
Negative mismatch score indicates LOSING form mismatch.

Losing Mismatch Score = weighted average of negative deltas:
  - PPG delta: (Last 5 PPG - Season PPG) / Season PPG           × 0.25
  - Goals delta: (Last 5 goals avg - Season goals avg)          × 0.20
  - Conceded delta: (Season conceded - Last 5 conceded) / Season × 0.15
  - Wins delta: (Last 5 win % - Season win %)                   × 0.20
  - Clean sheets delta: (Last 5 CS % - Season CS %)             × 0.20
```

**Home/Away Context Weighting:**
```
If team is playing at home (their strength venue):
  Final Score = Base Score × 1.25
```

**Losing Streak Penalty:**
```
If team has lost 3+ of last 5: additional -5% (making negative score worse)
```

**Thresholds:**
- **Strong Mismatch:** Score ≤ -25% decline
- **Moderate Mismatch:** Score -15% to -24% decline
- **No Mismatch:** Score > -15%

**Output:**
- Single worst mismatch recommendation per fixture
- Negative score = Losing Form Mismatch (cold streak)
- Include: all delta percentages, losing streak indicator
- Flag: "Cold streak - potentially overvalued"
- Same factor tracking as UC-010

**Status:** `Implemented`

---

#### UC-012: Over Corners Recommendations

**Goal:** Identify fixtures likely to have high corner counts (Over 9.5, Over 10.5).

**User Story:** As a user, I want to see which matches are likely to have many corners for over corners bets.

**Data Required:**
- Team corners won averages (home/away, season + form)
- Team corners conceded averages (opponent's overall as proxy)
- corners_potential, corners_o85_potential, corners_o95_potential, corners_o105_potential from API
- Goals scored averages (for playing style assessment)
- League positions (for match context)

**Logic:**
```
Expected Corners Calculation:

1. Base Expected per Team:
   - Home Expected = (home corners won avg + opponent conceded) / 2
   - Away Expected = (away corners won avg + opponent conceded) / 2

2. If form data available, blend:
   - Home Expected = (season * 0.6) + (form * 0.4)
   - Away Expected = (season * 0.6) + (form * 0.4)

3. Base Expected = Home Expected + Away Expected

4. API Potential Adjustment (20% influence):
   - O95 contribution: 9.5 + ((O95% - 50) / 50) × 2
   - O105 contribution: 10.5 + ((O105% - 50) / 50) × 2
   - Final = (base × 0.80) + (apiAdjustment × 0.20)

5. Apply Playing Style Multiplier:
   - Home goals ≥ 2.0/game: +5%
   - Home goals < 1.0/game: -5%
   - Away goals ≥ 1.5/game: +5%
   - Away goals < 0.8/game: -5%

6. Apply Match Context Multiplier:
   - Position diff ≤ 3: × 1.10 (close rivals)
   - Position diff 4-6: × 1.05 (competitive)

7. Apply Recent Trend Multiplier:
   - Form avg > season avg by 15%+: × 1.10
   - Form avg < season avg by 15%+: × 0.90
```

**Confidence Determination:**
```
Market selection is driven by expected corners only (line must be consistent
with the prediction). API potentials may boost confidence, never the line.

Strong Over:
  - Expected corners ≥ 12 → market Over 10.5
  - Expected 10–11.9 with strong API O95/O105 → still Over 9.5, confidence STRONG

Moderate Over:
  - Expected corners 10–11.9 without strong API → Over 9.5, MODERATE

Strong Under:
  - Expected corners ≤ 8 → market Under 8.5
  - Expected 8.1–9.5 with weak API O85/O95 → still Under 9.5, confidence STRONG

Moderate Under:
  - Expected corners 8.1–9.5 without weak API → Under 9.5, MODERATE
```

**Market Selection:**
- **Over 10.5 Corners:** Expected ≥ 12
- **Over 9.5 Corners:** Expected 10–11.9
- **Under 9.5 Corners:** Expected 8.1–9.5
- **Under 8.5 Corners:** Expected ≤ 8.0

**Output:**
- Single recommendation per fixture (Over or Under)
- Include: expected corners, market, confidence
- Factors tracked:
  - `expectedCorners` - final calculated expected
  - `formDataAvailable` - data availability flag
  - `homeCornersWonAvg` / `awayCornersWonAvg` - corners won
  - `homeConcededAvg` / `awayConcededAvg` - corners conceded
  - `homeFormCornersAvg` / `awayFormCornersAvg` - form data
  - `apiCornersO85Potential` / `apiCornersO95Potential` / `apiCornersO105Potential` - API potentials
  - `apiConfidenceBoostApplied` - whether API boosted confidence (not the line)
  - `playingStyleMultiplier` - attacking style adjustment
  - `matchContextMultiplier` - position-based adjustment
  - `trendMultiplier` / `trendDirection` - form trend adjustment
  - `homePosition` / `awayPosition` / `positionDifference` - league standings

**Status:** `Implemented`

---

#### UC-013: Under Corners Recommendations

**Goal:** Identify fixtures likely to have low corner counts (Under 9.5, Under 8.5).

**User Story:** As a user, I want to see which matches are likely to have few corners for under corners bets.

**Data Required:**
- Same as UC-012 (handled by same engine)

**Logic:**
```
Handled by CornersRecommendationEngine (same as UC-012).

The engine returns UNDER_CORNERS type when expected corners fall below thresholds.

Under Detection:
- Expected corners ≤ 9.5: triggers Under recommendation
- Expected corners ≤ 8.0: Under 8.5 (STRONG)
- Expected corners 8.1–9.5: Under 9.5 (MODERATE, or STRONG if API corner potentials are weak)
- Weak API potentials boost confidence only — they must not move the line to Under 8.5
  when expected corners are still above 8.0
```

**Thresholds:**
- **Strong Under (Under 8.5):** Expected corners ≤ 8.0
- **Moderate Under (Under 9.5):** Expected corners 8.1–9.5
- **API confidence boost:** O85 / O95 weak → STRONG on Under 9.5 only

**Market Selection:**
- **Under 8.5 Corners:** Expected ≤ 8.0 only
- **Under 9.5 Corners:** Expected 8.1–9.5

**Output:**
- Returns UNDER_CORNERS recommendation type
- Same factor tracking as UC-012

**Status:** `Implemented`

---

#### UC-014: Clean Sheet Recommendations

**Goal:** Identify teams likely to keep a clean sheet in upcoming fixtures.

**User Story:** As a user, I want to see which teams are likely to keep clean sheets for defensive betting markets.

**Data Required:**
- Clean sheet percentages (home/away, season + form)
- Goals conceded averages
- xGA (expected goals against) for defensive quality
- Opponent failed to score percentage
- Opponent xG (expected goals) for attacking threat

**Logic:**
```
*Base Weights (when xG data IS available - total = 1.0):*
Clean Sheet Score = weighted average of:
  - Team clean sheet % (season, home/away)        × 0.15
  - Team clean sheet % (last 5)                   × 0.15
  - Team goals conceded avg inverse (season)      × 0.10
  - Team goals conceded avg inverse (last 5)      × 0.10
  - Team xGA score                                × 0.15
  - Opponent failed to score % (season)           × 0.10
  - Opponent failed to score % (last 5)           × 0.10
  - Opponent xG score                             × 0.15

*Redistributed Weights (when xG data NOT available - total = 1.0):*
Clean Sheet Score = weighted average of:
  - Team clean sheet % (season, home/away)        × 0.20
  - Team clean sheet % (last 5)                   × 0.20
  - Team goals conceded avg inverse (season)      × 0.15
  - Team goals conceded avg inverse (last 5)      × 0.15
  - Opponent failed to score % (season)           × 0.15
  - Opponent failed to score % (last 5)           × 0.15
```

**xG-Based Assessment:**
```
Team Defensive xGA Rating (xGA per game):
  - <0.80 xGA = Elite defense (score: 90)
  - 0.80-1.10 xGA = Strong (score: 75)
  - 1.10-1.40 xGA = Average (score: 50)
  - >1.40 xGA = Leaky (score: 25)

Opponent Attacking xG Rating (xG per game):
  - <0.80 xG = Poor creators (score: 90) - good for clean sheet
  - 0.80-1.10 xG = Below average (score: 75)
  - 1.10-1.50 xG = Average (score: 50)
  - >1.50 xG = Strong creators (score: 25) - bad for clean sheet
```

**Defensive Strength Multiplier:**
```
Goals Conceded Rating:
  - <0.75 per game = Elite (× 1.20)
  - 0.75-1.00 per game = Strong (× 1.10)
  - 1.00-1.25 per game = Average (× 1.00)
  - >1.25 per game = Weak (× 0.85)
```

**Opponent Attacking Weakness Multiplier:**
```
Failed to Score Rating:
  - >40% failed to score = Poor attack (× 1.20)
  - 30-40% = Below average (× 1.10)
  - 20-30% = Average (× 1.00)
  - <20% = Strong attack (× 0.80)
```

**Hot Defensive Streak Bonus:**
```
If team has 3+ clean sheets in last 5 form matches: × 1.15
```

**Recent Conceded Penalty:**
```
If team conceded in all recent matches (0 CS in form): × 0.90
```

**xG Regression Adjustments:**
```
Team Regression Risk:
  - If actual conceded < xGA by 20%+: × 0.90 (likely to regress)

Opponent Overperformance:
  - If opponent actual goals < xG by 20%+: × 1.10 (opponent likely to regress)
```

**Thresholds:**
- **Strong:** Clean Sheet Score ≥ 70%
- **Moderate:** Clean Sheet Score 50-69%
- **Weak:** Clean Sheet Score < 50%

**Output:**
- Single best clean sheet candidate per fixture
- Include: team defensive stats, opponent attacking stats, confidence level
- Factors tracked:
  - `team` / `isHomeTeam` - candidate info
  - `xgDataAvailable` - data availability flag
  - `teamCleanSheetSeasonPct` / `teamCleanSheetFormPct` - CS percentages
  - `teamXgaPerGame` / `teamXgaScore` / `teamDefensiveXgRating` - defensive xG
  - `opponentFailedToScoreSeasonPct` / `opponentFailedToScoreFormPct` - opponent FTS
  - `opponentXgPerGame` / `opponentXgScore` / `opponentAttackingXgRating` - opponent xG
  - `defensiveRatingMultiplier` / `opponentWeaknessMultiplier` - rating adjustments
  - `hotDefensiveStreak` - 3+ consecutive CS flag
  - `concededInAllRecent` - no CS in form flag
  - `xgRegressionRisk` - team conceding below xGA
  - `opponentXgOverperformance` - opponent scoring below xG
  - `riskFlags` / `positiveIndicators` - summary lists

**Status:** `Paused` — removed from site boards, Elite, and Results metrics pending recalibration (early hit rate ~21%).

---

#### UC-015: First Half Goals Recommendations

**Goal:** Predict likelihood of goals in the first half (Over 0.5 HT, Over 1.5 HT).

**User Story:** As a user, I want to see which matches are likely to have first half goals.

**Data Required:**
- o05HT_potential, o15HT_potential from API
- First half goals stats (uses total goals × 0.45 as proxy)
- Team goals scored/conceded timing patterns
- BTTS HT percentage (season BTTS % as proxy)
- xG data (when available)

**Implementation:** `FirstHalfGoalsRecommendationEngine.java`

**Logic (with xG data available):**
```
First Half Goals Score = weighted average of:
  - API o05HT_potential                           × 0.15
  - API o15HT_potential                           × 0.10
  - Home team 1H goals scored proxy (×0.45)       × 0.12
  - Away team 1H goals scored proxy (×0.45)       × 0.12
  - Home team 1H goals conceded proxy (×0.45)     × 0.08
  - Away team 1H goals conceded proxy (×0.45)     × 0.08
  - BTTS season % (home team)                     × 0.05
  - BTTS season % (away team)                     × 0.05
  - Home team xG avg (× 0.45 for 1H proxy)        × 0.10
  - Away team xG avg (× 0.45 for 1H proxy)        × 0.10
  - Combined xGA factor                           × 0.05
```

**Dynamic Weights (when xG NOT available):**
```
Redistributed weights (total = 1.0):
  - API o05HT_potential                           × 0.20
  - API o15HT_potential                           × 0.15
  - Home team 1H goals scored proxy               × 0.15
  - Away team 1H goals scored proxy               × 0.15
  - Home team 1H goals conceded proxy             × 0.10
  - Away team 1H goals conceded proxy             × 0.10
  - BTTS season % (home team)                     × 0.075
  - BTTS season % (away team)                     × 0.075
```

**xG-Based Assessment (multipliers applied sequentially):**
```
Combined xG Rating:
  - Home xG + Away xG > 3.0 = High-scoring potential (× 1.20)
  - 2.5-3.0 = Above average (× 1.10)
  - 2.0-2.5 = Average (× 1.0)
  - <2.0 = Low-scoring potential (× 0.85)

xG Regression Adjustment:
  - Both teams underperforming xG (actual < xG × 0.85): (× 1.10)
  - Both teams overperforming xG (actual > xG × 1.15): (× 0.90)
```

**Fast Starter Detection (via API potentials):**
```
Implied Ratio = O05HT_potential / max(50, O25_potential)
  - Ratio > 0.825 = Fast starters detected (× 1.20)
  - Ratio < 0.45 = Slow starters detected (× 0.85)
```

**Early Conceder Assessment:**
```
Combined Conceded Avg (home + away):
  - > 2.5 per game = Both teams vulnerable defensively (× 1.15)
  - < 1.5 per game = Strong combined defense (× 0.90)
```

**Recent Form Adjustment (using O1.5 as proxy):**
```
Total O1.5 in form (out of 10 matches):
  - ≥ 8 matches with O1.5 = Hot form (× 1.10)
  - ≤ 4 matches with O1.5 = Cold form (× 0.90)
```

**Thresholds:**
- **Strong:** Score ≥ 75%
- **Moderate:** Score 60-74%
- **Weak (filtered):** Score < 60%
- **Minimum filter:** Expected 1H goals ≥ 0.8

**Market Selection:**
- **Over 1.5 HT:** Expected 1H goals ≥ 1.3 AND score ≥ 75% AND O15HT potential ≥ 55%
- **Over 0.5 HT:** Default market

**Enhanced Factor Tracking:**
- `expected1HGoals`, `expectedFullTimeGoals`, `firstHalfRatioUsed` (0.45)
- `home1HScoredProxyAvg`, `away1HScoredProxyAvg`
- `home1HConcededProxyAvg`, `away1HConcededProxyAvg`
- `homeBttsSeasonPct`, `awayBttsSeasonPct`
- `apiO05HtPotential`, `apiO15HtPotential`
- `xgDataAvailable`, `homeXgForAvgHome`, `awayXgForAvgAway`
- `combinedXg`, `home1HXgProxy`, `away1HXgProxy`
- `xgRating`, `homeXgPerformance`, `awayXgPerformance`, `xgRegressionOutlook`
- `fastStarterImpliedRatio`, `fastStarterStatus`
- `combinedConcededAvg`, `earlyConcedeStatus`
- `homeO15RecentForm`, `awayO15RecentForm`, `totalO15InForm`, `recentFormStatus`
- `positiveIndicators`, `riskFlags`

**Status:** `Implemented`

---

#### UC-016: Second Half Goals Recommendations

**Goal:** Predict likelihood of goals in the second half (Over 0.5 2H, Over 1.5 2H).

**User Story:** As a user, I want to see which matches are likely to have second half goals.

**Data Required:**
- Second half goals stats (uses total goals × 0.55 as proxy)
- Team goals scored/conceded timing patterns
- Cards average as proxy for late game intensity
- xG data (when available)
- Draw percentages for match situation factor

**Implementation:** `SecondHalfGoalsRecommendationEngine.java`

**Logic (with xG data available):**
```
Second Half Goals Score = weighted average of:
  - Home team 2H goals scored proxy (×0.55)       × 0.12
  - Away team 2H goals scored proxy (×0.55)       × 0.12
  - Home team 2H goals conceded proxy (×0.55)     × 0.08
  - Away team 2H goals conceded proxy (×0.55)     × 0.08
  - Home team xG avg (× 0.55 for 2H proxy)        × 0.10
  - Away team xG avg (× 0.55 for 2H proxy)        × 0.10
  - Combined xGA factor                           × 0.05
  - Late game intensity factor (cards)            × 0.10
  - Fitness/stamina indicator (goal difference)   × 0.10
  - Match situation factor (draw % + attacking)   × 0.15
```

**Dynamic Weights (when xG NOT available):**
```
Redistributed weights (total = 1.0):
  - Home team 2H goals scored proxy               × 0.18
  - Away team 2H goals scored proxy               × 0.18
  - Home team 2H goals conceded proxy             × 0.12
  - Away team 2H goals conceded proxy             × 0.12
  - Late game intensity factor                    × 0.15
  - Fitness/stamina indicator                     × 0.10
  - Match situation factor                        × 0.15
```

**xG-Based Assessment:**
```
Combined xG Rating:
  - Home xG + Away xG > 3.0 = High-scoring potential (× 1.20)
  - 2.5-3.0 = Above average (× 1.10)
  - 2.0-2.5 = Average (× 1.0)
  - <2.0 = Low-scoring potential (× 0.85)
```

**Late Goals Tendency (Strong Finisher Profile):**
```
Based on combined goals avg + clean sheet %:
  - Combined goals ≥ 3.0 AND avg CS% < 30% = Strong finisher (× 1.25)
  - Combined goals ≥ 2.5 = Balanced (× 1.05)
  - Combined goals < 2.0 = Front-loaded (× 0.90)
```

**Late Conceder Profile:**
```
Based on combined conceded avg + clean sheet %:
  - Combined conceded ≥ 2.5 AND avg CS% < 25% = Vulnerable late (× 1.20)
  - Combined conceded < 1.5 AND avg CS% > 40% = Strong late defense (× 0.90)
```

**Late Game Intensity Factor:**
```
Combined Cards Average:
  - ≥ 4.0 cards per game = High intensity matchup (× 1.10)
  - < 2.5 cards per game = Low intensity (× 0.95)

Intensity Score = min(100, (combinedCards / 6.0) × 100)
```

**Fitness/Stamina Indicator:**
```
Based on goal difference per game:
  - Positive GD = better fitness/ability to push late
  - Score = 50 + (avgGoalDiff × 25), clamped 0-100
```

**Match Situation Factor:**
```
Blend of draw likelihood (40%) and attacking intent (60%):
  - High draw % + high goals avg = competitive 2H expected
  - Combined goals indicator = min(100, (homeGoals + awayGoals) × 30)
```

**Thresholds:**
- **Strong:** Score ≥ 75%
- **Moderate:** Score 60-74%
- **Weak (filtered):** Score < 60%
- **Minimum filter:** Expected 2H goals ≥ 0.9

**Market Selection:**
- **Over 1.5 2H:** Expected 2H goals ≥ 1.5 AND score ≥ 75%
- **Over 0.5 2H:** Default market

**Enhanced Factor Tracking:**
- `expected2HGoals`, `expectedFullTimeGoals`, `secondHalfRatioUsed` (0.55)
- `home2HScoredProxyAvg`, `away2HScoredProxyAvg`
- `home2HConcededProxyAvg`, `away2HConcededProxyAvg`
- `homeCardsAvg`, `awayCardsAvg`, `combinedCardsAvg`, `lateGameIntensityScore`
- `homeGoalDifferencePerGame`, `awayGoalDifferencePerGame`, `fitnessIndicatorScore`
- `homeDrawPct`, `awayDrawPct`, `matchSituationScore`
- `xgDataAvailable`, `homeXgForAvgHome`, `awayXgForAvgAway`
- `combinedXg`, `home2HXgProxy`, `away2HXgProxy`, `xgRating`
- `combinedGoalsAvg`, `avgCleanSheetPct`, `finisherProfile`
- `combinedConcededAvg`, `lateConcedeProfile`
- `intensityProfile`
- `positiveIndicators`, `riskFlags`

**Status:** `Implemented`

---

#### UC-017: Match Result Recommendations

**Goal:** Predict Home Win outcomes for upcoming fixtures. Away tips are paused pending recalibration. Draw picks are deferred to UC-019 (`DrawRecommendationEngine`).

**User Story:** As a user, I want to see predicted home winners for upcoming fixtures.

**Data Required:**
- Team PPG (home/away, season + form)
- Win/Draw/Loss percentages
- Goals scored/conceded
- xG and xGA
- odds_ft_1, odds_ft_2 (for value / confidence only — not in the probability model)
- League positions

**Implementation:** `MatchResultRecommendationEngine.java`

**Logic (with xG data available):**
```
Home Win Probability = weighted average of:
  - Home team home win % (season)                 × 0.16
  - Home team home win % (form, sample-blended)   × 0.16
  - Away team away loss % (season)                × 0.11
  - Away team away loss % (form, sample-blended)  × 0.11
  - Home team home PPG normalized                 × 0.11
  - Away team away PPG inverse normalized         × 0.10
  - Home team xG vs Away team xGA comparison      × 0.15
  - Goal difference comparison                    × 0.10

Away Win Probability = (same structure inverted)

Draw Probability = 100% - Home Win % - Away Win %
  (bounded to 15-35% range; tracked in factors only)
```

**Dynamic Weights (when xG NOT available):**
```
Redistributed weights (total = 1.0):
  - Home team home win % (season)                 × 0.20
  - Home team home win % (form, sample-blended)   × 0.20
  - Away team away loss % (season)                × 0.13
  - Away team away loss % (form, sample-blended)  × 0.13
  - Home team home PPG normalized                 × 0.13
  - Away team away PPG inverse normalized         × 0.10
  - Goal difference comparison                    × 0.11
```

**Odds usage (P1 — single role):**
```
Odds are NOT an input to the probability model.
They are used only after a Home/Away side is chosen:
  valueVsOdds = modelProbability - impliedProbability
```

**xG (P1 — applied once):**
```
xG comparison is included only in the base weighted average above.
Dominance multipliers are tracked in factors for transparency but are
NOT re-applied to probabilities (avoids double-counting).
```

**Form Momentum Factor (multiplier, sample-dampened):**
```
Venue form sample = wins + draws + losses at venue (home or away).
  - sample < 3: ignore momentum (× 1.0)
  - sample 3–4: compute raw multiplier, then dampen toward 1.0 by sample/5
  - sample ≥ 5: apply raw multiplier fully

Raw multiplier:
  - 5 wins OR (4 wins + 1 draw) OR perfect thin sample (all wins, sample ≥ 3) = Hot streak (× 1.20)
  - 3+ wins = Good form (× 1.10)
  - Mixed results = Neutral (× 1.0)
  - 3+ losses = Poor form (× 0.85)
  - 5 losses OR perfect thin losing sample = Crisis (× 0.70)

Form % in the base model is blended toward season % when sample < 5.
```

**Home Advantage Factor:**
```
Flat 8% probability boost to home team win probability
Home team: +8%
Away team: -4%
```

**League Position Gap Factor:**
```
Position Difference = |Home position - Away position|
  - Gap ≥ 10 places: Favor higher team × 1.20
  - Gap 6-9 places: Favor higher team × 1.10
  - Gap 3-5 places: Slight favor × 1.05
  - Gap 0-2 places: No adjustment (1.0)
```

**Motivation Factor:**
```
Position 1-2 (Title race): × 1.15
Position 3-5 (European qualification): × 1.10
Position ≥ 17 (Relegation battle): × 1.15
Other positions: × 1.0
```

**Recommendation selection (P2):**
```
Only Home may be recommended (Away tips paused until recalibrated).
If Away win probability is higher than Home → no tip.
Draw is never emitted by this engine — use UC-019 Draw engine.
```

**Thresholds (P0):**
- **Strong:** Probability ≥ 62% AND (no outcome odds OR (odds ≤ 2.50 AND value vs odds ≥ 5%))
- **Moderate:** Probability ≥ 55% (also: probability ≥ 62% with long odds or no value edge)
- **Weak (filtered):** Probability < 55%

**Enhanced Factor Tracking:**
- `homeWinProbability`, `drawProbability`, `awayWinProbability`
- `drawsDeferredToDrawEngine` (always true)
- `awayTipsPaused` (always true while Away side is paused)
- `valueVsOdds`
- `oddsFt1`, `oddsFtX`, `oddsFt2`
- `impliedHomeWinPct`, `impliedDrawPct`, `impliedAwayWinPct`
- `homePosition`, `awayPosition`, `positionGap`
- `xgDataAvailable`, `xgDominanceAppliedAsMultiplier` (false)
- `homeXgForAvg`, `awayXgForAvg`, `homeXgAgainstAvg`, `awayXgAgainstAvg`
- `homeXgDominance`, `awayXgDominance`
- `homeXgDominanceMultiplier`, `awayXgDominanceMultiplier` (informational)
- `homeFormMomentumMultiplier`, `awayFormMomentumMultiplier`
- `homeFormStatus`, `awayFormStatus` (Hot streak/Good form/Neutral/Poor form/Crisis)
- `homeFormWins`, `homeFormDraws`, `homeFormLosses`, `homeFormSampleSize` (same for away)
- `homeAdvantageApplied` (8%)
- `homeMotivation`, `awayMotivation` (Title race/European qualification/Relegation battle)
- `homePpgHome`, `awayPpgAway`
- `homeGoalDifference`, `awayGoalDifference`
- `positiveIndicators`, `riskFlags`

**Status:** `Implemented`

---

#### UC-018: Home/Away Recommendations

**Goal:** Identify teams with significant performance gaps between home and away matches.

**User Story:** As a user, I want to spot teams that are much stronger at home or away to inform my bets.

**Data Required:**
- Home vs away PPG comparison
- Home vs away goals scored/conceded
- Home vs away win/draw/loss percentages
- xG home vs away splits

**Logic:**
```
Home/Away Disparity Score = average of metric disparities:

PPG Disparity:
  - (Home PPG - Away PPG) / Overall PPG × 100

Win Rate Disparity:
  - Home win % - Away win %

Goals Scored Disparity:
  - (Home goals avg - Away goals avg) / Overall goals avg × 100

Goals Conceded Disparity:
  - (Away conceded avg - Home conceded avg) / Overall conceded avg × 100

xG Disparity (if available):
  - (Home xG avg - Away xG avg) / Overall xG avg × 100
```

**Home Specialist Detection:**
```
Strong Home Specialist:
  - Home PPG > Away PPG by 0.8+
  - Home win % > Away win % by 25%+
  - Home goals avg > Away goals avg by 40%+

Moderate Home Specialist:
  - Home PPG > Away PPG by 0.5-0.79
  - Home win % > Away win % by 15-24%
  - Home goals avg > Away goals avg by 25-39%
```

**Away Specialist Detection:**
```
Strong Away Specialist:
  - Away PPG > Home PPG by 0.3+ (rare - away is harder)
  - Away win % > Home win % by 10%+
  - Away goals avg ≥ Home goals avg

Moderate Away Specialist:
  - Away PPG within 0.2 of Home PPG
  - Away win % within 5% of Home win %
  - Consistent away performer (away PPG > 1.5)
```

**Poor Traveler Detection:**
```
Strong Poor Traveler:
  - Away PPG < 0.8
  - Away win % < 20%
  - Away goals avg < 0.8

Moderate Poor Traveler:
  - Away PPG 0.8-1.0
  - Away win % 20-30%
```

**Fortress Detection:**
```
Home Fortress:
  - Home win % > 70%
  - Home loss % < 15%
  - Home goals conceded avg < 0.8
```

**Recent Form Context:**
```
Last 5 Home/Away Form:
  - Compare recent home form to season home average
  - Compare recent away form to season away average
  - Flag if recent form diverges from season pattern
```

**Thresholds (recalibrated Aug 2026):**
- **Strong Specialist:** Disparity score ≥ 40%, xG data available, form not declining
- **Moderate Specialist:** Disparity score ≥ 25% (capped at Moderate when xG missing or form declining)
- **No pick:** Disparity &lt; 25%, negative home edge, or failed gates

**Publishing rules:**
- **Home-only picks** — Home Specialist, Home Fortress, Poor Traveler (back home team); Away Specialist **paused**
- Home Specialist requires **positive home PPG and win-rate edge** (not OR-only with negative disparity)
- Goals/conceded averages use **venue match counts**, not overall `matchesPlayed`

**Status:** `Implemented` (recalibrated)

**Form Divergence Detection:**
```
Form diverges from season if PPG difference > 0.3:
  - If formPpg > seasonPpg: "Improving"
  - If formPpg < seasonPpg: "Declining"
```

**Factors Tracked:**
```
- team / isHomeTeam / classification / recommendation
- overallDisparityScore
- homePpg / awayPpg / ppgDisparity
- homeWinPct / awayWinPct / winDisparity
- homeGoalsAvg / awayGoalsAvg / goalsDisparity
- homeConcededAvg / awayConcededAvg / concededDisparity
- xgDataAvailable / xgDisparity / homeXgAvg / awayXgAvg
- formDataAvailable / formPpgDivergence / formGoalsDivergence
- formDivergesFromSeason / formStatus
- candidatesFound / allCandidates (list with team/classification/score/confidence)
- positiveIndicators / riskFlags
```

**Positive Indicators Tracked:**
- Strong disparity score (≥ 40%)
- Home fortress classification
- Opponent is poor traveler
- xG data confirms disparity

**Risk Flags Tracked:**
- Recent form declining from season pattern
- No xG data available for validation

**Output:**
- Best candidate recommendation per fixture
- Classification: Strong/Moderate Home Specialist, Strong/Consistent Away Performer, Strong/Moderate Poor Traveler, Home Fortress
- All candidates found listed in factors
- Recommendation: Back Home Win, Back Away Win, Fade Away based on classification

**Status:** `Implemented`

---

#### UC-019: Draw Recommendations

**Goal:** Identify fixtures likely to end in a draw.

**User Story:** As a user, I want to find matches with high draw probability for draw betting.

**Data Required:**
- Team draw percentages (home/away, season + form)
- Evenly matched stats comparison
- Goals scored/conceded averages
- xG comparison
- odds_ft_x from API

**Logic:**
```
Draw Probability Score = weighted average of:
  - Home team draw % (home, season)               × 0.12
  - Away team draw % (away, season)               × 0.12
  - Home team draw % (last 5)                     × 0.10
  - Away team draw % (last 5)                     × 0.10
  - Evenly matched indicator                      × 0.20
  - Low-scoring potential indicator               × 0.15
  - xG similarity factor                          × 0.11
  - Implied probability from odds_ft_x            × 0.10
```

**Evenly Matched Indicator:**
```
Statistical Similarity Score:
  - PPG difference < 0.3 = High similarity (1.25)
  - PPG difference 0.3-0.5 = Moderate (1.10)
  - PPG difference 0.5-0.8 = Slight gap (1.0)
  - PPG difference > 0.8 = Mismatch (0.75)

League Position Proximity:
  - Within 3 places = High (1.20)
  - Within 6 places = Moderate (1.10)
  - Within 10 places = Slight (1.0)
  - 10+ places apart = Mismatch (0.80)

Combined: Average of both scores
```

**Low-Scoring Potential Indicator:**
```
Combined Goals Assessment:
  - Both teams avg < 1.2 goals/game = High draw potential (1.25)
  - Both teams avg 1.2-1.5 goals/game = Moderate (1.15)
  - One team < 1.2, other > 1.5 = Mixed (1.0)
  - Both teams avg > 1.5 goals/game = Lower draw chance (0.85)

Defensive Strength:
  - Both teams concede < 1.0/game = Tight game likely (1.20)
  - Both teams concede > 1.5/game = Goals likely, not draw (0.80)
```

**xG Similarity Factor:**
```
xG Comparison:
  - |Home xG - Away xG| < 0.2 = Very similar (1.25)
  - |Home xG - Away xG| 0.2-0.4 = Similar (1.10)
  - |Home xG - Away xG| 0.4-0.6 = Moderate gap (1.0)
  - |Home xG - Away xG| > 0.6 = Clear difference (0.80)
```

**Draw Specialist Detection:**
```
High Draw Team:
  - Draw % > 35% (season) = Draw-prone (1.20)
  - Draw % 28-35% = Above average (1.10)
  - Draw % 20-28% = Average (1.0)
  - Draw % < 20% = Decisive team (0.85)
```

**Recent Draw Form:**
```
Last 5 Matches:
  - 3+ draws in last 5 = Draw-heavy (1.25)
  - 2 draws in last 5 = Moderate (1.10)
  - 1 draw in last 5 = Normal (1.0)
  - 0 draws in last 5 = Decisive (0.85)
```

**Match Context Adjustments:**
```
Stakes Assessment:
  - Nothing to play for both teams = Draw more likely (1.15)
  - Both teams need points equally = Draw possible (1.10)
  - One team desperate, other safe = Less likely draw (0.85)
  - Derby/rivalry = Can go either way (1.0)
```

**Referee Tendency Factor:**
```
Referee Draw Rate (from UC-003 data):
  - Referee draw % > 30% = Draw-friendly (1.20)
  - Referee draw % 25-30% = Above average (1.10)
  - Referee draw % 20-25% = Average (1.0)
  - Referee draw % < 20% = Decisive games (0.90)

Cards Per Match Correlation:
  - < 3 cards/match = Controlled games, draw likely (1.10)
  - 3-4 cards/match = Normal (1.0)
  - > 4 cards/match = Chaotic, less predictable (0.95)

Referee Sample Size:
  - < 5 appearances: Weight × 0.5 (low confidence)
  - 5-10 appearances: Weight × 0.8
  - > 10 appearances: Full weight (1.0)
```

**Time of Season Factor:**
```
Season Phase Assessment:
  - Final 3 matchdays, both mid-table = Dead rubber (1.25)
  - Final 5 matchdays, positions settled = Relaxed (1.15)
  - Opening 5 matchdays = Feeling out period (1.10)
  - Mid-season = Normal intensity (1.0)
  - Run-in with stakes = High intensity, decisive (0.85)

Fixture Timing:
  - Midweek fixture after weekend game = Fatigue, cagey (1.10)
  - Post-international break = Disruption, draw risk (1.10)
  - Normal weekend fixture = Standard (1.0)
```

**Thresholds:**
- **Strong:** Not emitted (capped at Moderate until draw score calibration improves hit rate)
- **Moderate:** Draw Score ≥ 28
- **Weak:** Draw Score < 28

**Factors Tracked:**
```
- drawScore - overall draw probability score
- homeDrawPctSeason / awayDrawPctSeason - season draw percentages
- homeDrawsLast5 / awayDrawsLast5 - recent draw counts
- ppgDifference / evenlyMatchedScore - team similarity
- homePosition / awayPosition / positionDifference - league positions
- homeGoalsAvg / awayGoalsAvg / lowScoringScore - attacking data
- homeConcededAvg / awayConcededAvg / defensiveStrengthScore - defensive data
- xgDataAvailable / homeXgAvg / awayXgAvg / xgDifference / xgSimilarityScore - xG metrics
- drawSpecialistMultiplier / recentDrawFormMultiplier - team multipliers
- refereeCardsMultiplier / matchContextMultiplier - context multipliers
- refereeDrawPct / refereeAppearances / refereeCardsPerMatch - referee data
- drawOdds / impliedProbability / valueVsOdds - betting data
- positiveIndicators / riskFlags - analysis summary
```

**Positive Indicators Tracked:**
- Very evenly matched teams (PPG diff < 0.3)
- Draw specialist team(s) involved (draw % > 35%)
- Very similar xG profiles (diff < 0.2)
- Both teams defensively strong (concede < 1.0/game)
- Recent draw-heavy form (2+ draws in last 5)
- Draw-friendly referee (draw % > 30%)

**Risk Flags Tracked:**
- No xG data available for validation
- Both teams high-scoring - goals more likely than draw
- Both teams defensively weak - goals likely
- Neither team has drawn recently (decisive form)
- Mismatch in stakes - one team desperate

**Output:**
- Ranked list of fixtures by draw probability
- Include: both team draw %, similarity scores, xG comparison
- Value flag: Compare our probability vs implied odds probability
- Flag: "Draw specialists meeting" when both teams draw > 30%
- Dynamic weight redistribution when xG data unavailable

**Status:** `Implemented`

---

#### UC-024: Double Chance Recommendations

**Goal:** Identify fixtures where backing two outcomes (Home/Draw or Draw/Away) offers strong probability with reduced risk.

**User Story:** As a user, I want to find matches where a double chance bet offers good value with high confidence, reducing my risk while maintaining reasonable returns.

**Data Required:**
- Team win percentages (home/away)
- Draw percentages
- Points per game (PPG)
- League position
- Recent form (last 5 matches)
- Match odds for 1X, X2 markets

**Logic:**
```
Double Chance Probability Calculation:

Home/Draw (1X):
  Combined Probability = Home Win % + Draw %
  
  Factors:
  - Home team PPG home ≥ 1.5                    (strong home side)
  - Away team win % away < 25%                  (poor travelers)
  - Home team unbeaten at home > 60%            (fortress factor)
  - Position: Home team higher ranked           (quality advantage)

Draw/Away (X2):
  Combined Probability = Draw % + Away Win %
  
  Factors:
  - Away team PPG away ≥ 1.3                    (competent away)
  - Home team win % home < 40%                  (weak home form)
  - Away team unbeaten away > 50%               (road warriors)
  - Away team higher ranked by 5+ places        (quality advantage)
```

**When to Recommend 1X (Home/Draw):**
```
Strong Indicators:
  - Home team rarely loses at home (< 20% loss rate)
  - Away team struggles away (< 30% win rate away)
  - Home team in top half, away team in bottom half
  - Home team's last 5 home: 0-1 losses
  
Combined Probability Threshold:
  - 1X Probability ≥ 70% = Strong recommendation
  - 1X Probability 60-69% = Moderate recommendation
```

**When to Recommend X2 (Draw/Away):**
```
Strong Indicators:
  - Away team strong on road (> 35% win rate away)
  - Home team poor at home (< 40% win rate home)
  - Away team clearly higher quality (10+ positions above)
  - Home team's last 5 home: 2+ losses
  
Combined Probability Threshold:
  - X2 Probability ≥ 65% = Strong recommendation
  - X2 Probability 55-64% = Moderate recommendation
```

**Value Calculation:**
```
Market odds typically:
  - 1X: 1.20 - 1.60
  - X2: 1.30 - 1.80

Value = Calculated Probability - Implied Probability
Recommend when Value ≥ 5%
```

**Scoring (recalibrated):**
- Fortress / poor-traveler / road-warrior adjustments multiply the relevant side's **win**
  probability **before** the three outcomes are normalised, so home + draw + away always sums to
  100 and the combined double chance cannot be inflated past what they imply.
- Venue win and draw rates are shrunk toward league baselines (45% home win, 28% away win, 25%
  draw) with four matches of pseudo-evidence, so thin early-season records cannot dominate.
- The published score is an even blend of the model estimate and the **de-vigged** market
  probability. Implied prices are normalised across 1/X/2 to strip the bookmaker margin first;
  the margin is retained only for the synthesized price shown to the user.

**Confidence Levels:**
- **Strong:** Blended probability ≥ 78% (1X) / ≥ 74% (X2) **and a market price is available**
- **Moderate:** Blended probability ≥ 66% (1X) / ≥ 62% (X2)
- **Weak:** Below moderate (filter out)

Strong no longer requires the model to beat the price by 5%+. That rule promoted the fixtures
where the model disagreed most with the market, and since the model ran hot those were its worst
picks — Strong landed 11 points *below* Moderate over 291 settled picks. Requiring a price instead
means Strong can only be claimed when the market has been folded into the estimate.

**xG Integration:**
```
xG Score = (Team xG for avg + Opponent xG against avg) / 2
Normalized to 0-100 scale

Weight: 15% of probability when xG available
Redistributed to other factors when not available
```

**Weights:**
```
With xG:
  - Win % component: 35%
  - PPG component: 20%
  - Position component: 15%
  - Form component: 15%
  - xG component: 15%

Without xG:
  - Win % component: 40%
  - PPG component: 25%
  - Position component: 18%
  - Form component: 17%
```

**Factors Tracked:**
```
- homeWinProbability / drawProbability / awayWinProbability
- homeDrawCombined / drawAwayCombined
- implied1X / value1X / impliedX2 / valueX2
- homeFortress / awayPoorTraveler / awayRoadWarrior / homeWeakAtHome
- homePosition / awayPosition / positionGap
- homeWinRateHome / homeLossRateHome / awayWinRateAway / awayLossRateAway
- homeLast5Wins / homeLast5Losses / awayLast5Wins / awayLast5Losses
- xgDataAvailable / homeXgFor / awayXgFor / homeXgAgainst / awayXgAgainst
- positiveIndicators / riskFlags
```

**Positive Indicators Tracked:**
- Home team is a fortress (rarely loses at home)
- Away team struggles on the road
- Away team is a road warrior (strong away record)
- Home team weak at home
- 1X/X2 offers value vs odds
- Home/away team significantly higher ranked

**Risk Flags Tracked:**
- No xG data available for validation
- High probability but negative value vs odds
- Both teams have strong home/away characteristics

**Output:**
- Market: "Home/Draw (1X)" or "Draw/Away (X2)"
- Combined probability percentage with value vs odds
- Individual probabilities breakdown (Home %, Draw %, Away %)
- Team characteristics (fortress, road warrior, poor traveler)
- Calculated double chance odds from individual markets
- Positive indicators and risk flags

**Status:** `Implemented`

---

#### UC-025: Result + BTTS Combo Recommendations

**Goal:** Identify fixtures where a combined Result + Both Teams To Score bet has strong probability, offering enhanced odds with good confidence.

**User Story:** As a user, I want to find matches where I can confidently back a result combined with BTTS for better odds than a single result bet.

**Data Required:**
- Team win percentages (home/away)
- BTTS percentages (overall, home, away)
- Goals scored/conceded averages
- Clean sheet percentages (inverse relationship)
- Failed to score percentages
- Match result odds + BTTS odds (if available)

**Logic:**
```
Result + BTTS Probability = Result Probability × BTTS Probability

Example:
  - Home Win probability: 55%
  - BTTS probability: 65%
  - Home Win + BTTS Yes: 55% × 65% = 35.75%
```

**Markets to Consider:**
```
1. Home Win + BTTS Yes
   Requirements:
   - Home win probability ≥ 50%
   - BTTS probability ≥ 55%
   - Home team scores frequently (avg > 1.3/game)
   - Home team concedes regularly (avg > 0.8/game)
   
2. Away Win + BTTS Yes
   Requirements:
   - Away win probability ≥ 45%
   - BTTS probability ≥ 55%
   - Away team scores away (avg > 1.0/game away)
   - Away team concedes away (avg > 0.7/game away)
   
3. Draw + BTTS Yes (Score Draw)
   Requirements:
   - Draw probability ≥ 25%
   - BTTS probability ≥ 60%
   - Both teams score regularly
   - Evenly matched (PPG difference < 0.4)
```

**Key Exclusion Criteria (applied per market, not globally):**
```
Do NOT recommend a market when:
  - The backed winner keeps clean sheets in > 40% of matches (win to nil likely)
  - The winner's opponent fails to score in > 35% of matches
  - The winner's own goals requirements are not met (see Markets above)

Exclusions are evaluated per candidate market, so a strong Away + BTTS
candidate is not discarded because the home side keeps clean sheets.
```

**BTTS Probability Calculation:**
```
Inputs blended by availability:

API potential + xG:  home BTTS 0.28 + away BTTS 0.28 + api 0.24 + xG 0.20
API potential only:  home BTTS 0.35 + away BTTS 0.35 + api 0.30
xG only:             home BTTS 0.40 + away BTTS 0.40 + xG 0.20
Neither:             average of home and away season BTTS %
```

**xG BTTS Indicator:**
```
homeExpected = (home xG for + away xG against) / 2
awayExpected = (away xG for + home xG against) / 2

Both teams must threaten, so the limiting side governs:
  indicator = clamp((min(homeExpected, awayExpected) / 1.2) x 100)
```

**xG Dominance (applied to win probability):**
```
Win probability is blended 85% base / 15% xG dominance when xG is available.

dominance = clamp(50 + ((team xG for - opponent xG for) x 40))
```

**Confidence Calculation:**
```
Combined Score = Result Probability × BTTS Probability

Adjustments (multiplicative):
  x 1.10 if both teams are BTTS-heavy in recent form (>= 60% each)
  x 1.05 if both teams concede regularly (< 25% clean sheet rate)
  x 0.90 if either team has > 35% clean sheet rate
  x 0.95 if either team fails to score > 30% of games
```

> **Deviation from spec:** the spec's "+10% if both teams scored in last 3
> meetings" requires head-to-head history, which is not present in
> `FixtureContext`. Recent-form BTTS percentage (last 5, home/away split) is
> used as a proxy. Adding true H2H support would require a new data source and
> entity, so it is tracked separately rather than approximated silently.

**Thresholds:**
- **Strong:** Adjusted probability ≥ 35%
- **Moderate:** Adjusted probability ≥ 28%
- **Weak:** Adjusted probability < 28% (filter out)

**Factors Tracked:**
```
- homeWinProbability / drawProbability / awayWinProbability
- bttsProbability / resultProbability
- combinedProbability / adjustedProbability / selectedResultType
- homeScoredAvg / awayScoredAvg / homeConcededAvg / awayConcededAvg
- homeCleanSheetPct / awayCleanSheetPct
- homeFailedToScorePct / awayFailedToScorePct
- homeBttsPctSeason / awayBttsPctSeason / apiBttsPotential
- homeBttsPctForm / awayBttsPctForm
- xgDataAvailable / homeXgFor / homeXgAgainst / awayXgFor / awayXgAgainst
- xgBttsIndicator
- confidenceAdjustmentMultiplier / adjustmentsApplied
- positiveIndicators / riskFlags
```

**Positive Indicators Tracked:**
- Strong BTTS probability (≥ 65%)
- Neither team keeps many clean sheets
- Both teams score at a healthy rate
- xG profiles support both teams scoring
- Net positive confidence adjustment

**Risk Flags Tracked:**
- No xG data available for validation
- A team keeps clean sheets often - win to nil risk
- A team fails to score often - BTTS at risk
- No recent form data - head-to-head proxy unavailable

**Output:**
- Market: "{Home Team} + BTTS", "{Away Team} + BTTS", or "Draw + BTTS"
- Combined and adjusted probability percentages
- Individual breakdown (Result %, BTTS %)
- Applied confidence adjustments listed in the description
- Key supporting stats (goals avg, clean sheet %, failed to score %, xG)

**Status:** `Implemented`

---

#### UC-026: Top vs Bottom Recommendations

**Goal:** Identify fixtures featuring extreme league position mismatches (top teams vs bottom teams) and provide appropriate betting recommendations.

**User Story:** As a user, I want to identify matches where there's a significant quality gap between teams, allowing me to back the stronger team with confidence or find value in unlikely outcomes.

**Data Required:**
- League positions for both teams
- Points per game
- Goal differences
- Recent form (last 5)
- Head-to-head history (upsets?)
- Home/away performance splits

**Logic:**
```
Position Mismatch Detection:
  - Extreme: Top 3 vs Bottom 3 (≥ 14 position gap)
  - Strong: Top 5 vs Bottom 5 (≥ 10 position gap)
  - Moderate: Top 6 vs Bottom 6 (≥ 8 position gap)
```

**Recommendation Types:**

**1. Back the Favorite (Strong Team Win):**
```
When to recommend:
  - Position gap ≥ 10 places
  - Favorite PPG ≥ 1.8
  - Underdog PPG ≤ 1.0
  - Favorite's goal difference positive by 15+
  - Underdog's goal difference negative by 10+

Confidence Boost:
  + If favorite is at home
  + If underdog has poor away record (< 20% win rate)
  + If favorite won last H2H convincingly (2+ goals)
```

**2. Handicap/Goals Line (Alternative Market):**
```
When favorite odds are too short (< 1.40):
  - Recommend: Favorite -1.5 goals
  - Or: Over 2.5/3.5 goals
  
Logic:
  - Top teams often score 2+ against bottom teams
  - Bottom teams often concede multiple goals
```

**3. Upset Alert (Underdog Value):**
```
When to flag potential upset:
  - Underdog at home
  - Underdog's recent form improving (W or D in last 2)
  - Favorite's away form poor (< 50% win rate away)
  - Historical H2H shows underdog can compete
  - Favorite has fixture congestion (mid-week game prior)

If upset factors present:
  - Flag: "Upset Watch" 
  - Recommend: Double Chance (1X) for home underdog
  - Or: Draw at value odds
```

**4. BTTS in Mismatch:**
```
Surprisingly common scenario:
  - Top team attacks freely, bottom team desperate
  - Bottom teams at home often score 1 against big teams
  
Recommend BTTS when:
  - Bottom team scores in 50%+ of home games
  - Top team concedes in 40%+ of away games
  - Top team's clean sheet away < 35%
```

**Position Gap Scoring:**
```
Gap Score = |Home Position - Away Position|

Quality Score Calculation:
  - PPG difference
  - Goal difference comparison  
  - Recent form comparison (points from last 5)
  - Head-to-head record

Combined Mismatch Score = Gap Score × Quality Score Multiplier
```

**Confidence Levels:**
- **Strong:** Gap ≥ 12, home favorite only, quality ≥ 65, PPG/GD filters align, no upset watch cap
- **Moderate:** Gap ≥ 10, home favorite only, quality ≥ 50, PPG/GD filters mostly align
- **Weak:** Filter out (includes away favorites, gap &lt; 10, failed quality filters, or ≥ 3 upset factors)

**Publishing rules (Aug 2026 recalibration):**
- Home table favorites only (away favorites deferred to Match Result recalibration)
- Moderate gap floor **10** places; Strong gap floor **12** (Banker at **14** + quality ≥ 70)
- Venue PPG filters: favorite home PPG ≥ 1.5 (Moderate) / 1.8 (Strong); underdog away PPG ≤ 1.2 / 1.0
- Strong GD filters: favorite season GD ≥ +10; underdog season GD ≤ −5
- **Upset Watch:** ≥ 2 upset factors caps Strong → Moderate and may pivot primary market to **Home/Draw (1X)**; ≥ 3 upset factors → no pick
- **Short favorite odds** (&lt; 1.40): pivot primary to **Over 2.5/3.5 Goals** or **Favorite −1.5** when mismatch quality is high
- **BTTS mismatch** surfaced as an alternative market suggestion in factors when underdog scores away ≥ 50% and favorite concedes home ≥ 40%

**Settlement (UC-033):** Primary market graded on FT scoreline — team win (draw = LOSS), Over/Under goals totals, −1.5 handicap, or Double Chance 1X when pivoted.

**Status:** `Implemented` (recalibrated)

---

#### UC-038: Over 1.5 Goals Recommendations

**Goal:** Identify fixtures likely to finish with **2 or more total goals** and recommend the **Over 1.5 Goals** line as its own board (not mixed with Over 2.5 / 3.5).

**User Story:** As a user, I want a dedicated Over 1.5 section so I can back the safer total-goals line when both teams consistently clear 1.5, without scanning the generic Over Goals board.

**Data Required:**
- Goals scored/conceded averages (season + form)
- Season Over 1.5 percentages (`seasonOver15Percentage_overall`)
- Recent-form Over 1.5 percentages when last-x data exists
- `o15_potential` from API
- `odds_ft_over15`
- xG for/against averages (when available)

**API Source(s):** football-data-api.com `/league-matches`, `/league-teams?include=stats`, `/lastx`

**Distinct from UC-006:** UC-006 is a high-scoring engine that may select Over 2.5 or Over 3.5. UC-038 always markets **Over 1.5 Goals** and weights Over 1.5 hit-rates plus `o15_potential`.

**Logic:**

The score is the modelled probability that the fixture clears the line, blended from two
independent estimates of the same event.

*Expected goals — every goal-volume signal feeds this one number:*
```
Season   = (Home scored + Away scored + Home conceded + Away conceded) / 2   weight 0.50
xG       = (Home xG for + Away xG for + Home xG against + Away xG against) / 2   weight 0.30
Form     = same shape from last-x scored/conceded averages                   weight 0.20
Expected goals = weighted mean, renormalised over whichever inputs are present
```

*Poisson half:*
```
P(2+ goals) = 1 - e^-λ (1 + λ)        where λ = expected goals
```

*Empirical half — signals already expressed as probabilities of the event:*
```
Team rate = mean of home and away Over 1.5 %      weight 0.30
            (each: 0.5 × season + 0.5 × form when form exists)
API o15_potential                                  weight 0.40
Empirical = weighted mean, renormalised over whichever inputs are present
```

*Final:*
```
Score = 0.5 × Poisson + 0.5 × Empirical
```

**No additive boosts.** The previous scoring summed rescaled goal averages, over-line percentages
and the API potential into an index, then added a high-scoring boost (+5), an xG boost (+4) and an
expected-goals lift (up to +18) on top. All three measured goal volume, which the index already
contained, so a busy fixture was counted three times and could gain 27 points over its base. That
saturated the 100 clamp, tying every high-scoring fixture at exactly 100 — and since Elite breaks
ties on shortest price, the board went to whichever tie had the least generous odds. Routing all
goal evidence through λ and taking a Poisson tail removes both the double-counting and the ceiling.

**Filters (all must pass):**
- Expected goals ≥ 1.8
- Score ≥ Moderate threshold

**Thresholds:** rebased for the probability score. Over 1.5 is a high base-rate market — roughly
three quarters of all fixtures clear it — so a moderate call must sit well above the league
average or the board is recommending nothing.
- **Strong:** Score ≥ 82%
- **Moderate:** Score 72–81%
- **Weak:** Score < 72% (filtered out)

**Market:** Always `Over 1.5 Goals`. Odds from `odds_ft_over15`.

**UI:** Recommendations section **Over 1.5 Goals**, immediately after **Top vs Bottom** and before **Over 2.5 Goals**. Elite-eligible (UC-036). Settlement: FT total vs 1.5 (UC-033).

**Status:** Implemented

---

#### UC-039: Over 2.5 Goals Recommendations

**Goal:** Identify fixtures likely to finish with **3 or more total goals** and recommend the **Over 2.5 Goals** line as its own board (never stepping up to Over 3.5).

**User Story:** As a user, I want a dedicated Over 2.5 section so I can back that line when both teams’ Over 2.5 rates support it, without the generic Over Goals engine swapping in Over 3.5.

**Data Required:**
- Goals scored/conceded averages (season + form)
- Season Over 2.5 percentages (`seasonOver25Percentage_overall`)
- Recent-form Over 2.5 percentages when last-x data exists
- `o25_potential` from API
- `odds_ft_over25`
- xG for/against averages (when available)

**API Source(s):** football-data-api.com `/league-matches`, `/league-teams?include=stats`, `/lastx`

**Distinct from UC-006:** UC-006 may select Over 3.5 when expected goals and Over 3.5 rates are high. UC-039 always markets **Over 2.5 Goals** and weights Over 2.5 hit-rates plus `o25_potential` more heavily than UC-006.

**Logic:** Same Poisson/empirical blend as UC-038, substituting Over 2.5 percentages and
`o25_potential`, and requiring three goals rather than two:
```
P(3+ goals) = 1 - e^-λ (1 + λ + λ²/2)
```

**No additive boosts** — see UC-038 for why they were removed.

**Filters (all must pass):**
- Expected goals ≥ 2.5
- Score ≥ Moderate threshold

**Thresholds:** Over 2.5 is a far lower base rate than Over 1.5 — around half of fixtures — so the
same probability score means something very different here. Even a heavy 4.0 expected-goals
fixture is only about a 76% chance to clear three goals, so the thresholds sit well below UC-038's.
- **Strong:** Score ≥ 68%
- **Moderate:** Score 58–67%
- **Weak:** Score < 58% (filtered out)

**Market:** Always `Over 2.5 Goals`. Odds from `odds_ft_over25`.

**UI:** Recommendations section **Over 2.5 Goals**, after **Over 1.5 Goals** and before **Draw**. Elite-eligible (UC-036). Settlement: FT total vs 2.5 (UC-033).

**Status:** Implemented

---

### Website

_Use cases for the web application interface._

---

#### UC-020: Homepage - Competition Overview

**Goal:** Provide an at-a-glance view of all supported competitions with upcoming fixture counts, allowing users to quickly navigate to areas of interest.

**User Story:** As a user, I want to land on the homepage and see a list of competitions with visual indicators showing how many fixtures are available, so that I can quickly identify which leagues have upcoming matches and navigate to them.

**Data Required:**
- League list (from `LeagueRepository`)
- Fixture counts per league (from `FixtureRepository` - upcoming fixtures grouped by league)
- League metadata: name, country, logo/badge

**UI Components:**

1. **Header/Navigation**
   - Site name: "AccaBaccaGlory"
   - Home link with 🏠 icon

2. **Country Groups**
   - Competitions grouped by country
   - Countries sorted alphabetically (A-Z)
   - Country header with flag icon (if available)
   - **Hide competitions with 0 fixtures**
   - **Hide countries with no visible competitions**

3. **Competition Card (Expandable)**
   - Each card shows:
     - League badge/logo with white background square (for visibility)
     - League name
     - Fixture count icon with number (e.g., 📅 10)
     - Expand/collapse indicator (chevron)
   - Clicking a card expands to reveal fixtures below
   - Expanded section shows:
     - List of upcoming fixtures for that competition
     - Each fixture displays: home team vs away team, date/time
     - Each fixture links to `/fixtures/{fixtureId}` (fixture detail + recommendations)
     - Clicking the competition header again collapses the section

4. **Fixture Detail Page** (`/fixtures/{fixtureId}`)
   - Match header: home vs away, kickoff, stadium, gameweek
   - Recommendations for that fixture from `GET /api/recommendations/fixture/{id}`
   - Grouped by recommendation type; shortlist star works as elsewhere
   - Empty state when no engines produce a pick
   - Back link to `/fixtures`
   - Reverse bridge: recommendation rows on other pages link fixture names to this page

5. **Empty State**
   - Message when no upcoming fixtures available
   - "No upcoming fixtures" or similar

**API Endpoint:**
```
GET /api/leagues/overview
```

**Response Structure:**
```json
{
  "countries": [
    {
      "country": "England",
      "countryCode": "GB-ENG",
      "competitions": [
        {
          "leagueId": 1,
          "name": "Premier League",
          "logoUrl": "...",
          "fixtureCount": 2,
          "fixtures": [
            {
              "fixtureId": 101,
              "homeTeam": "Arsenal",
              "awayTeam": "Chelsea",
              "matchDate": "2026-07-22T15:00:00Z"
            },
            {
              "fixtureId": 102,
              "homeTeam": "Liverpool",
              "awayTeam": "Man United",
              "matchDate": "2026-07-22T17:30:00Z"
            }
          ]
        }
      ]
    },
    {
      "country": "Germany",
      "countryCode": "DE",
      "competitions": [
        {
          "leagueId": 2,
          "name": "Bundesliga",
          "logoUrl": "...",
          "fixtureCount": 1,
          "fixtures": [
            {
              "fixtureId": 201,
              "homeTeam": "Bayern Munich",
              "awayTeam": "Dortmund",
              "matchDate": "2026-07-23T14:30:00Z"
            }
          ]
        }
      ]
    }
  ],
  "totalFixtures": 3,
  "lastUpdated": "2026-07-21T03:00:00Z"
}
```

**Acceptance Criteria:**
- [x] Site branded as "AccaBaccaGlory"
- [x] Home navigation link with 🏠 icon
- [x] Homepage displays competitions with fixtures only
- [x] Competitions with 0 fixtures are hidden
- [x] Competitions are grouped by country
- [x] Countries are sorted alphabetically (A-Z)
- [x] League logos have white background for visibility
- [x] Each competition shows fixture count with icon
- [x] Clicking a competition expands to show fixtures
- [x] Clicking again collapses the fixture list
- [x] Expanded fixtures show home vs away and date/time
- [x] Clicking a fixture opens fixture detail with its recommendations
- [x] Recommendation rows link fixture names to fixture detail
- [ ] Page loads within 500ms (cached data)
- [x] Responsive layout works on mobile and desktop

**Status:** Reviewed

---

#### UC-021: Recommendations Page - Section-Based Display

**Goal:** Display all recommendation types in dedicated sections, each showing the top 5 picks across all competitions and fixtures.

**User Story:** As a user, I want to see recommendations organized by type with the best picks for each category, so I can quickly find the strongest opportunities in each market.

**Data Required:**
- All recommendations grouped by type (from `RecommendationService`)
- Top 5 per type, sorted by score/confidence

**UI Components:**

1. **Page Header**
   - Title: "Recommendations"
   - Days ahead filter (12 hours, 24 hours, 3 days, 7 days)

2. **Kickoff urgency**
   - Filter chips: All kickoffs / Soon (next 3 hours) / Today / Tomorrow
   - Chip labels show fixture counts for Soon / Today / Tomorrow
   - Sort control: Best score (default) or Soonest kickoff
   - Selecting Soon/Today/Tomorrow switches sort to soonest kickoff
   - Rows show relative kickoff labels (`in 45m`, `Today`, `Tomorrow`) with urgency colouring
   - Footer hint when picks are starting within 3 hours

3. **Recommendation Sections**
   - One section per recommendation type
   - Section title with icon/badge
   - Shows top 5 recommendations for that type
   - Each recommendation card shows:
     - Home vs Away teams
     - Match date/time (relative when soon/today/tomorrow)
     - Confidence level (Strong/Moderate)
     - Score/probability
     - Key factors

3. **Section Types:**
   - Both Teams To Score (BTTS)
   - Over Goals (UC-006 — Over 2.5 or Over 3.5)
   - Over 1.5 Goals (UC-038) — display order after Top vs Bottom
   - Over 2.5 Goals (UC-039) — display order after Over 1.5, before Draw
   - Under Goals
   - Booking Points
   - Value Bet
   - Winning Form Mismatch
   - Losing Form Mismatch
   - Over Corners
   - Under Corners
   - Clean Sheet
   - First Half Goals
   - Second Half Goals
   - Match Result
   - Home/Away Specialist
   - Draw
   - Double Chance, Result + BTTS, Top vs Bottom

4. **Empty Section**
   - If a section has no recommendations, show subtle message or hide section

5. **Elite Picks (UC-036)**
   - Cross-market top 10 board at the **bottom** of the page (aggregation, not a new engine)
   - See UC-036 for ranking / eligibility rules

**API Endpoint:**
```
GET /api/recommendations/grouped?daysAhead=7
```

**Response Structure:**
```json
{
  "BTTS": [
    {
      "fixtureId": 101,
      "homeTeamName": "Arsenal",
      "awayTeamName": "Chelsea",
      "matchDateUnix": 1721750400,
      "type": "BTTS",
      "confidence": "STRONG",
      "score": 0.85,
      "market": "BTTS Yes",
      "description": "Both teams score regularly...",
      "factors": { ... }
    }
  ],
  "OVER_GOALS": [ ... ],
  ...
}
```

**Acceptance Criteria:**
- [x] Page displays sections for all recommendation types
- [x] Each section shows up to 5 recommendations
- [x] Recommendations sorted by score (highest first)
- [x] Days ahead filter works across all sections
- [x] Empty sections are hidden automatically
- [x] Responsive grid layout for recommendation cards

**Status:** Reviewed

---

#### UC-022: Settings - Theme Toggle (Light/Dark Mode)

**Goal:** Provide users with a settings menu to customize their experience, starting with a light/dark mode toggle.

**User Story:** As a user, I want to access settings from the header to toggle between light and dark modes, so that I can customize the appearance to my preference.

**Data Required:**
- User preference stored in localStorage
- System preference detection (prefers-color-scheme)

**UI Components:**
1. **Settings Icon Button** (header, top-right)
   - Gear/cog icon (⚙️)
   - Opens dropdown menu on click
   - Closes when clicking outside

2. **Settings Dropdown Menu**
   - Positioned below settings icon
   - Contains theme toggle option
   - Expandable for future settings

3. **Theme Toggle**
   - Label: "Theme"
   - Options: Light / Dark / System
   - Visual indicator of current selection

**Behavior:**
- Theme preference persisted to localStorage
- "System" option follows OS preference
- Immediate visual feedback on toggle
- Smooth transition between themes

**CSS Variables (Theme Support):**
- `--bg-primary`: Main background color
- `--bg-secondary`: Card/section backgrounds
- `--bg-header`: Header background
- `--text-primary`: Main text color
- `--text-secondary`: Muted text color
- `--border-color`: Border colors
- `--accent-color`: Primary accent/brand color

**API Endpoints:**
- None (client-side only)

**Acceptance Criteria:**
- [x] Settings icon visible in header (top-right)
- [x] Dropdown opens on click
- [x] Dropdown closes when clicking outside
- [x] Theme toggle switches between Light/Dark/System
- [x] Theme preference persisted across page reloads
- [x] Smooth CSS transitions between themes
- [x] All components respect theme variables

**Status:** Implemented

---

#### UC-023: Shortlist Page - Save & Manage Picks

**Goal:** Allow users to save recommendations to a personal shortlist for easy reference and tracking, with the ability to add/remove picks at any time.

**User Story:** As a user, I want to add recommendations to a shortlist so that I can keep track of the picks I'm interested in and easily reference them later.

**Data Required:**
- Shortlisted recommendation IDs stored in localStorage
- Full recommendation data fetched from existing API endpoints

**UI Components:**

1. **Add to Shortlist Button (on Recommendations Page)**
   - Icon button on each recommendation row/card
   - Star or bookmark icon (☆ empty, ★ filled)
   - Visual feedback when toggled
   - Persists selection to localStorage

2. **Shortlist Page**
   - Accessible via navigation: "Shortlist"
   - Displays all shortlisted recommendations
   - Same card/row format as recommendations page
   - Empty state when no items shortlisted

3. **Remove from Shortlist**
   - Same toggle button removes item
   - Immediate visual feedback
   - Item removed from shortlist page

4. **Shortlist Counter (Optional)**
   - Badge on navigation showing shortlist count
   - Updates dynamically as items are added/removed

**Behavior:**
- Shortlist persisted to localStorage (survives page refresh)
- Adding a pick: Click empty star → fills, item added to shortlist
- Removing a pick: Click filled star → empties, item removed from shortlist
- Shortlist page filters to only show saved recommendations
- Works across all recommendation types

**Storage Structure:**
```json
{
  "shortlist": [
    {
      "fixtureId": 101,
      "type": "BTTS",
      "addedAt": "2026-07-25T01:00:00Z"
    },
    {
      "fixtureId": 102,
      "type": "MATCH_RESULT",
      "addedAt": "2026-07-25T01:05:00Z"
    }
  ]
}
```

**API Endpoints:**
- None (client-side only, uses existing recommendation APIs)

**Acceptance Criteria:**
- [ ] Star/bookmark icon visible on each recommendation
- [ ] Clicking icon toggles shortlist state
- [ ] Visual distinction between shortlisted and non-shortlisted items
- [ ] Shortlist page accessible from navigation
- [ ] Shortlist page displays all saved recommendations
- [ ] Removing from shortlist updates page immediately
- [ ] Shortlist persists across page reloads (localStorage)
- [ ] Empty state shown when shortlist is empty
- [ ] Works on both desktop and mobile layouts

**Status:** Draft

---

#### UC-027: Export Shortlist - Share & Export Picks

**Goal:** Allow users to export their shortlisted picks in shareable formats (image, text, or link) for sharing on social media or with friends.

**User Story:** As a user, I want to export my shortlist as an image or text so that I can share my picks with friends or on social media.

**Data Required:**
- Shortlist items from localStorage/context
- Full recommendation details (fixture, market, odds, confidence, date/time)
- Branding elements (logo, colors)

**Export Formats:**

1. **Image Export (Primary)**
   - Branded card/graphic with AccaBaccaGlory logo
   - Shows all shortlisted picks in a visually appealing format
   - Includes: Fixture, Selection, Odds, Confidence indicator
   - Date/time stamp of when exported
   - Optimized for social media sharing (Instagram story, Twitter)

2. **Text Export**
   - Plain text format for copying to clipboard
   - Format: `[Date] [Fixture] - [Selection] @ [Odds] ([Confidence])`
   - Includes total combined odds if multiple picks

3. **Share Link (Future)**
   - Generate unique URL that displays the shortlist
   - Link expires after X days or after matches complete

**UI Components:**
- Export button on Shortlist page (share icon)
- Export modal with format options
- Preview of export before sharing
- "Copy to clipboard" for text format
- "Download image" for image format
- Native share sheet integration (mobile)

**Image Generation Approach:**
- Use HTML Canvas or html2canvas library
- Server-side generation alternative for consistency
- Template with slots for picks data

**Acceptance Criteria:**
- [ ] Export button visible on Shortlist page when items exist
- [ ] Image export generates a branded graphic with all picks
- [ ] Text export copies formatted picks to clipboard
- [ ] Image can be downloaded or shared via native share
- [ ] Empty shortlist shows disabled export button
- [ ] Works on both desktop and mobile

**Design Notes:**
- Light mode design with compact cards
- Include league icons next to fixtures
- Combined odds displayed in footer
- AccaBaccaGlory branding in header

**Status:** `Implemented`

---

#### UC-028: Performance Tracking - Historical Hit Rates

**Goal:** Track the historical accuracy of recommendations to show users how well each recommendation type performs over time, building trust and helping users make informed decisions.

**User Story:** As a user, I want to see how accurate past recommendations have been so that I can understand which recommendation types are most reliable.

**Data Required:**
- Historical recommendations (stored before match starts)
- Match results (final scores, cards, corners, etc.)
- Outcome resolution (win/loss/void for each recommendation)

**Backend Requirements:**

1. **Recommendation Storage:**
   - Store all generated recommendations with timestamp
   - Include all factors and confidence levels
   - Link to fixture ID for result matching

2. **Result Processing:**
   - After match completion, fetch final results
   - Resolve each recommendation: WIN / LOSS / VOID / PUSH
   - Calculate running statistics per type

3. **Statistics Calculated:**
   - Hit rate % per recommendation type (last 7/30/90 days)
   - Hit rate by confidence level (Strong vs Moderate)
   - ROI % (if odds were tracked)
   - Streak tracking (current win/loss streak)
   - Total picks analyzed

**Database Schema Additions:**
```
RecommendationHistory:
  - id
  - fixtureId
  - type (RecommendationType)
  - market (e.g., "BTTS Yes", "Over 2.5")
  - confidence
  - score
  - odds (if available)
  - generatedAt
  - resolvedAt
  - outcome (WIN/LOSS/VOID/PUSH)
  - matchResult (stored JSON of relevant stats)
```

**UI Components:**

1. **Performance Dashboard Page** (`/performance`)
   - Overall hit rate summary
   - Hit rate by recommendation type (table/chart)
   - Filter by time period (7d / 30d / 90d / All time)
   - Filter by confidence level

2. **Type Performance Cards:**
   - Type name and icon
   - Hit rate % with trend indicator (↑↓)
   - Sample size (e.g., "based on 127 picks")
   - ROI % if odds tracked

3. **Visual Elements:**
   - Bar chart showing hit rates by type
   - Line chart showing hit rate over time
   - Color coding: Green (>55%), Amber (45-55%), Red (<45%)

**API Endpoints:**
- `GET /api/performance/summary` - Overall stats
- `GET /api/performance/by-type` - Stats per recommendation type
- `GET /api/performance/history` - Recent resolved picks

**Acceptance Criteria:**
- [ ] Recommendations are stored before matches start
- [ ] Results are fetched and outcomes resolved after matches
- [ ] Performance page shows hit rate by type
- [ ] Time period filter works correctly
- [ ] Confidence level breakdown available
- [ ] Stats update automatically after matches complete
- [ ] Minimum sample size indicator (e.g., "Not enough data" if < 10 picks)

**Status:** Superseded — see **Results** section (UC-031–UC-035). Performance hit-rate analytics live under UC-035.

---

#### UC-029: Push Notifications - High Confidence Alerts

**Goal:** Notify users when high-confidence recommendations become available, ensuring they don't miss valuable betting opportunities.

**User Story:** As a user, I want to receive notifications when strong recommendations are available so that I don't miss time-sensitive betting opportunities.

**Notification Triggers:**
1. **New Strong Recommendation** - When a STRONG confidence pick is generated
2. **Match Starting Soon** - Reminder for shortlisted picks (30 min / 1 hour before)
3. **Daily Digest** - Summary of best picks for the day (optional)

**Technical Approach:**

1. **Web Push Notifications (PWA)**
   - Service Worker registration
   - Push API subscription
   - Backend notification service

2. **Notification Preferences:**
   - Enable/disable all notifications
   - Choose which types to receive alerts for
   - Set quiet hours (no notifications between X and Y)
   - Frequency limit (max N notifications per day)

**Backend Requirements:**

1. **Push Subscription Storage:**
   ```
   PushSubscription:
     - id
     - endpoint
     - keys (p256dh, auth)
     - userId (optional, for logged-in users)
     - createdAt
     - preferences (JSON)
   ```

2. **Notification Service:**
   - Web Push library (e.g., web-push for Node.js)
   - Queue system for sending notifications
   - Rate limiting per subscriber

3. **Trigger Points:**
   - After recommendation generation (check for STRONG confidence)
   - Scheduled job for match reminders
   - Daily digest at configurable time

**UI Components:**

1. **Notification Settings** (in Settings dropdown)
   - Master toggle: Enable notifications
   - Per-type toggles for each recommendation type
   - Match reminder toggle + time selection
   - Daily digest toggle + time selection
   - Quiet hours configuration

2. **Permission Request Flow:**
   - Subtle prompt after user interaction (not on page load)
   - Explain value: "Get notified about high-confidence picks"
   - Respect "Block" - don't ask again

3. **Notification Content:**
   - Title: "🔥 Strong Pick Available"
   - Body: "[Home] vs [Away] - [Market] @ [Odds]"
   - Action: "View Pick" → Opens recommendation
   - Icon: AccaBaccaGlory logo

**Privacy Considerations:**
- Anonymous subscriptions (no account required)
- Clear unsubscribe option
- No tracking of notification interactions (beyond delivery)

**Acceptance Criteria:**
- [ ] Users can enable push notifications
- [ ] Permission prompt appears at appropriate time
- [ ] Notifications sent for STRONG confidence picks
- [ ] Match reminders sent for shortlisted items
- [ ] Settings allow granular control of notification types
- [ ] Quiet hours respected
- [ ] Unsubscribe works correctly
- [ ] Works on desktop browsers (Chrome, Firefox, Edge)
- [ ] Works on mobile browsers (where supported)

**Status:** Draft

---

#### UC-036: Elite Picks - Cross-Market Top Selections

**Goal:** Add an **Elite Picks** section at the **bottom** of the Recommendations page that surfaces the best **10** selections from across eligible recommendation types — a curated “best of board” rather than another single-market engine.

**User Story:** As a user, I want a shortlist of the strongest tips across every market so that I can quickly see the standout opportunities without scanning every section.

**Depends on:** Existing recommendation engines + UC-021 Recommendations page (grouped feed).

**Placement:**
- Recommendations page only
- Rendered **after** all market sections (bottom of page)

**What Elite Picks is (and is not):**
| Is | Is not |
|----|--------|
| A **ranking / aggregation** over already-generated picks | A new prediction engine with its own model |
| Fixed size: **top 10** (or fewer if the pool is smaller) | Unlimited “show all Strong” |
| Cross-type among %-style scores | A duplicate of one market section |

**Data Required:**
- Recommendations for the active **horizon** (`3d` / `7d`) only — not gated by kickoff / league / confidence / market filters
- Per pick: `type`, `market`, `confidence`, `score`, `odds`, fixture identity, league

**Core problem — scores are not comparable across types:**
Engines publish different `score` meanings. Elite v1 constrains the pool to probability / quality %-style types only.

---

##### Population rules (locked)

**Pool:**
1. Fetch / reuse grouped recommendations for the page **horizon** (`days` = 3 or 7)
2. Keep **STRONG** confidence only
3. Keep eligible %-style types only (below)

**Eligible types (v1):**
- `MATCH_RESULT`, `BTTS`, `DOUBLE_CHANCE`, `DRAW`, `OVER_GOALS`, `UNDER_GOALS`
- `OVER_15_GOALS`, `OVER_25_GOALS`
- `CLEAN_SHEET`, `RESULT_BTTS`, `TOP_VS_BOTTOM`
- `FIRST_HALF_GOALS`, `SECOND_HALF_GOALS`, `VALUE_BET`

**Excluded (v1):**
- `BOOKING_POINTS`, `OVER_CORNERS`, `UNDER_CORNERS`
- `HOME_AWAY_SPECIALIST`, `WINNING_FORM_MISMATCH`, `LOSING_FORM_MISMATCH`

**Rank:**
1. `score` descending
2. Tie-break: lower decimal odds (nulls last), then sooner kickoff

**Dedupe / diversity:**
- At most **one pick per fixture** (highest-ranked selection wins)
- At most **3 BTTS** picks on the Elite board (additional Strong BTTS are skipped so other markets can fill the top 10)

**Filters (locked):**
| Filter | Elite respects? |
|--------|-----------------|
| Horizon (3d / 7d) | **Yes** |
| Kickoff window | No |
| League | No |
| Confidence chip | No (always Strong) |
| Market filter | No |
| Search | No |

**Output:** Top **10** after rank + dedupe (or all candidates if &lt; 10). Hide section when empty.

**UI contract:**
- Section title: **Elite Picks**
- Same row component as other sections (`RecommendationRow`)
- Not a Market Filter dropdown option

**API:** Client-side from `GET /api/recommendations/grouped?daysAhead={horizon}` (v1). Dedicated elite endpoint deferred.

---

##### Decisions (locked)

| # | Topic | Choice |
|---|--------|--------|
| 1 | Confidence gate | **Strong only** |
| 2 | Score comparability | **%-style types only (v1)** |
| 3 | Fixture dedupe | **1 per fixture** |
| 3b | Per-type cap | **BTTS max 3** |
| 4 | Page filters | **Horizon only** |
| 5 | Corners / specialist / form later | Defer (percentile / separate board) |
| 6 | Hit-rate weighting (UC-035) | **No for v1** |
| 7 | Section position | **Bottom** |
| 8 | Implementation | **Client-side v1** |

**Acceptance Criteria:**
- [ ] Elite Picks section appears at the bottom of Recommendations
- [ ] Shows at most 10 Strong %-style picks, ≤1 per fixture, ≤3 BTTS
- [ ] Uses horizon window only; ignores other page filters
- [ ] Empty window → section hidden
- [ ] Early Kick-Off warnings still apply on Elite rows
- [ ] Shortlist / Info behave the same as other sections

**Status:** Reviewed — implementing

**Next Steps:**
- [x] Lock decisions with product
- [ ] Implement ranking helper + section on Recommendations page
- [ ] Optional follow-up: server endpoint + hit-rate-aware ranking

---

### Results

_Use cases for freezing daily picks, fetching completed match outcomes, settling win/loss, and presenting Results on the AccaBaccaGlory site._

**Product decisions (locked):**

| Decision | Choice |
|----------|--------|
| Snapshot scope | All **STRONG** and **MODERATE** picks (actionable; exclude WEAK) |
| Snapshot window | Fixtures **kicking off today** in brand timezone |
| Snapshot timing | Immediately **after** the daily FootyStats sync completes |
| Settlement v1 | Scoreline + corners + booking points (see UC-033) |
| Timezone | Brand timezone: `Europe/London` (AccaBaccaGlory) |
| Pending matches | Re-settlement retries until resolved or VOID |

**Daily rhythm (brand timezone):**

```text
Day N   scheduled sync (existing)     → refresh fixtures/odds/stats
Day N   then snapshot job             → freeze STRONG+MODERATE picks for kickoffs today
Day N+1 scheduled results job         → fetch completed results → settle → retry PENDING
        Results page                  → shows settled / pending picks by day
```

Exact cron times remain configurable; order is fixed: **sync → snapshot**, then next day **ingest → settle**.

---

#### UC-031: Daily Recommendation Snapshot

**Goal:** Persist a durable, immutable daily record of published picks so they can be graded after matches finish. Live recommendations are ephemeral (on-demand + short cache); without a snapshot, yesterday’s board cannot be graded fairly.

**User Story:** As the system, I want to freeze all Strong and Moderate picks for fixtures kicking off today after the daily sync so that we have a stable pick history to settle against results.

**Data Required:**
- Live recommendation engine output (production `Recommendation` model)
- Fixture kickoff (`matchDateUnix`) for “today” filtering
- Brand calendar day in `Europe/London`

**Decisions (locked):**
| Topic | Choice |
|-------|--------|
| Sync gate | Snapshot only when `SyncSummary.success` is true (`failedSeasons == 0`); otherwise skip |
| Same-day re-run | Upsert only rows that are still `PENDING` **and** whose kickoff is still in the future; never rewrite post-kickoff or settled rows |
| Types snapshotted | All actionable types (including corners/cards); settlement scope is UC-033 |
| Generation window | Generate for fixtures through end of London today (prefer `daysAhead` covering today only, not full 7-day board), then filter |
| Manual admin sync | On successful `POST /api/admin/sync`, chain the same snapshot job |
| factorsJson | Persist for debugging/transparency; not required on Results v1 UI |
| Timezone | Configurable property defaulting to `Europe/London` |

**Snapshot rules:**
- Trigger: after successful daily sync (scheduler pipeline) and after successful manual admin sync
- Include: confidence `STRONG` or `MODERATE` only
- Include: picks whose fixture kickoff falls on **today** in brand timezone
- Exclude: `WEAK` picks; fixtures kicking off on other calendar days
- Unique key: `(snapshotDate, fixtureId, type)` — type from the recommendation record
- Store denormalized display fields (team names, league, market, odds, score, description) so Results UI does not depend on live fixture rows that may later age out of the upcoming window

**Persisted fields (minimum):**
```
RecommendationSnapshot:
  - id
  - snapshotDate          (LocalDate, brand timezone)
  - fixtureId
  - homeTeamId / awayTeamId
  - homeTeamName / awayTeamName
  - matchDateUnix         (kickoff)
  - leagueId / leagueName / leagueImage
  - type                  (RecommendationType)
  - market                (selection string, e.g. "BTTS Yes", "Over 2.5")
  - confidence            (STRONG | MODERATE)
  - score
  - odds                  (nullable)
  - description
  - factorsJson           (optional; transparency)
  - generatedAt
  - outcome               (PENDING initially)
  - resolvedAt            (null until settled)
  - matchResultJson       (null until settled; relevant scoreline/stats)
```

**API Source(s):** Internal — `RecommendationService` after sync; no extra FootyStats call beyond the sync that just ran.

**Behavior:**
1. Confirm daily (or admin) sync completed successfully
2. Evict recommendation caches (existing post-sync step), then generate recommendations for today’s window
3. Filter to STRONG/MODERATE + kickoff date == today (brand timezone)
4. Upsert into snapshot storage per re-run policy above
5. Leave new/updated outcomes as `PENDING`

**Acceptance Criteria:**
- [ ] Snapshot runs only when sync reports success
- [ ] Only STRONG and MODERATE picks are stored
- [ ] Only brand-timezone-today kickoffs are stored
- [ ] Unique on `(snapshotDate, fixtureId, type)` — no duplicates
- [ ] Re-run does not alter post-kickoff or already-settled rows
- [ ] Snapshots survive cache eviction and recommendation regeneration
- [ ] Picks remain queryable by `snapshotDate`
- [ ] All new outcomes start as `PENDING`
- [ ] Manual admin sync chains snapshot on success

**Status:** Implemented

---

#### UC-032: Completed Match Result Ingest

**Goal:** Fetch final (or updated) match results for snapshotted fixtures so picks can be settled. Current sync keeps upcoming incomplete fixtures only and does not refresh completed scores.

**User Story:** As the system, I want to pull completed match data for yesterday’s snapshotted fixtures so that each pick can be marked win or loss.

**Data Required:**
- Snapshot rows still `PENDING` (or all snapshots for a target date)
- Match status + scoreline (FT goals; HT / 2H goals for half-goal markets)
- Corners/cards stored when present (graded in UC-033)

**Decisions (locked):**
| Topic | Choice |
|-------|--------|
| Fetch strategy | Bulk `GET /todays-matches?date=&timezone=` (paginated) per target date; `GET /match?match_id=` fallback for fixtureIds still missing or incomplete |
| Target dates | London **yesterday**, plus any `snapshotDate` with PENDING rows in the last **7 days** |
| Storage | Persist **`CompletedMatch`** keyed by `fixtureId` (status, scoreline, stats, `fetchedAt`); snapshot `matchResultJson` filled at settle time (UC-033) |
| HT / 2H | Store whenever the API provides them |
| Scheduling | Separate morning cron (configurable brand timezone); **not** chained to sync→snapshot |
| Manual trigger | Admin endpoint to ingest by date and/or all PENDING in lookback |
| After lookback | Stop automatic retries; remaining PENDING handled by UC-033 expiry/VOID policy |

**API Source(s):** FootyStats `/todays-matches`, `/match`; map via `MatchStatus`, `Scoreline`, `MatchStats` (existing WIP mapper).

**Behavior:**
1. Scheduled results job runs (default: morning Day N+1, brand timezone)
2. Resolve target dates (yesterday + PENDING lookback)
3. For each date: fetch all pages of todays-matches; upsert `CompletedMatch`
4. For PENDING snapshot fixtureIds still absent or `INCOMPLETE`: selective `getMatch` fallback
5. Admin `?date=` ingest uses the same fallback for PENDING fixtures on that snapshot date
6. Hand off fixtures with fresh data to settlement (UC-033) — same scheduler run is fine; use-case boundary remains ingest
7. Incomplete matches remain eligible until lookback expires

**Edge cases:**
- Match postponed / still incomplete → upsert status; leave snapshots PENDING for retry
- Suspended / canceled → upsert status for VOID in UC-033
- Partial data (FT in, HT missing) → store what is available; half-goal settle stays PENDING until data arrives or UC-033 policy applies
- API partial failure → do not wipe existing good `CompletedMatch` rows

**Acceptance Criteria:**
- [ ] Job can load results for fixtures no longer in the upcoming 7-day sync window
- [ ] Completed matches yield FT scoreline on `CompletedMatch`
- [ ] HT/2H stored when present
- [ ] Incomplete matches do not force LOSS; snapshots stay PENDING and retry within 7 days
- [ ] Canceled/suspended status is captured for VOID handling
- [ ] Job is safe to re-run (idempotent upserts)
- [ ] Admin can trigger ingest manually
- [ ] Automatic retries stop after 7-day lookback

**Status:** Implemented

---

#### UC-033: Pick Settlement (Win / Loss / Void)

**Goal:** Mark each snapshotted pick as a winner or loser (or void/pending/unsupported) using market-specific rules against ingested results.

**User Story:** As the system, I want to resolve each frozen pick against the match result so that the Results page can show which tips landed.

**Outcomes:**
| Outcome | Meaning |
|---------|---------|
| `PENDING` | Result not available yet, or required stats missing; will retry |
| `WIN` | Pick correct |
| `LOSS` | Pick incorrect |
| `VOID` | Match canceled/suspended, or unresolved after lookback expiry |
| `UNSUPPORTED` | Type/selection not graded (unknown or unparseable market) — not a failed tip |

(`PUSH` reserved if needed later for refunded lines; not required for v1.)

Hit-rate denominators (UC-034/035) use **only** `WIN` and `LOSS`.

**Decisions (locked):**
| Topic | Choice |
|-------|--------|
| Deferred markets | None for known recommendation types; unknown/unparseable → `UNSUPPORTED` |
| Team-to-win + draw | **LOSS** |
| Past 7-day lookback still unresolved | **VOID** (expired) |
| Half-goal markets in v1 | **Yes**; stay PENDING until HT/2H available |
| Corners / booking points | **Yes**; Yellow=10, Red=25; missing stats → PENDING; booking exact line → VOID (push) |
| VALUE_BET | Settle when `market` parses as a supported shape; else UNSUPPORTED |
| Re-settle terminal rows | **No** for WIN/LOSS/VOID; **yes** for corners/bookings previously marked UNSUPPORTED (catch-up) |
| Selection encoding | v1 parses `type` + `market` with unit tests; structured selection is a follow-up |

**Gate order (every PENDING row, plus catch-up UNSUPPORTED corners/bookings):**
1. Already terminal WIN/LOSS/VOID → skip
2. No `CompletedMatch` → PENDING
3. Status `INCOMPLETE` / `UNKNOWN` → PENDING
4. Status `CANCELED` / `SUSPENDED` → VOID
5. Status `COMPLETE` → run type grader
6. `snapshotDate` older than lookback and still unresolved → VOID

**Settlement scope v1 — grade using actual engine market strings:**

| RecommendationType | WIN when | Data |
|--------------------|----------|------|
| `BTTS` | `BTTS Yes` → both teams scored | FT |
| `OVER_GOALS` / `UNDER_GOALS` / `OVER_15_GOALS` / `OVER_25_GOALS` | FT total vs line in market (`Over/Under 1.5/2.5/3.5 Goals`) | FT |
| `MATCH_RESULT` | market is home name → home win; away name → away win; `Draw` → draw | FT |
| `DRAW` | FT draw (`market` = `Draw`) | FT |
| `DOUBLE_CHANCE` | `Home/Draw (1X)` → home or draw; `Draw/Away (X2)` → draw or away | FT |
| `RESULT_BTTS` | result leg and BTTS both true (`{Team} + BTTS` or `Draw + BTTS`) | FT |
| `CLEAN_SHEET` | named team (`{Team} Clean Sheet`) conceded 0 | FT |
| `TOP_VS_BOTTOM`, form mismatch, `HOME_AWAY_SPECIALIST` | named team won (draw = LOSS) | FT |
| `FIRST_HALF_GOALS` | HT total vs line (`Over 0.5/1.5 HT Goals` or `… First Half Goals`) | HT |
| `SECOND_HALF_GOALS` | 2H total vs line (prefer API 2H; else FT−HT) | HT+FT or 2H |
| `OVER_CORNERS` / `UNDER_CORNERS` | match corners total vs line (`Over/Under 8.5/9.5/10.5 Corners`) | corners |
| `BOOKING_POINTS` | (yellow×10 + red×25) vs line (`Over/Under 30/40/50 Booking Points`) | cards |
| `VALUE_BET` | Dispatch by market: scoreline shapes, corners, booking points | as above |

**Still `UNSUPPORTED`:**
- Unknown `type`
- Unparseable `market` string

**Settlement rules:**
- Prefer `type` + parsed `market`; match team names against denormalized snapshot home/away names
- Insufficient stats for that market → PENDING (retry), never LOSS
- On resolve: set `outcome`, `resolvedAt`, copy slim status/scoreline/corners/cards into `matchResultJson`

**Behavior:**
1. For each eligible PENDING snapshot (and catch-up UNSUPPORTED corners/bookings) with `CompletedMatch` (or expiry check)
2. Apply gate order, then type-specific grader
3. Persist outcome fields

**Acceptance Criteria:**
- [ ] All v1 types have explicit grader rules and unit tests against real market strings
- [ ] BTTS / O-U / match-result / draw / double chance / result+BTTS / clean sheet / team-win settle from FT
- [ ] Half-goal markets use HT (and 2H) correctly; missing HT → PENDING then VOID after lookback
- [ ] Corners settle from home+away corners; missing → PENDING
- [ ] Booking points settle with Yellow=10 / Red=25; missing cards → PENDING
- [ ] Canceled/suspended → VOID
- [ ] Incomplete → PENDING and retried within lookback
- [ ] Settlement is idempotent for already-resolved WIN/LOSS/VOID rows
- [ ] `matchResultJson` populated on resolve

**Status:** Implemented

---

#### UC-034: Results Page - Settled Picks Board

**Goal:** Add a **Results** section to the AccaBaccaGlory website that shows snapshotted picks and whether they won or lost. Layout/visual design TBD; this use case defines the product contract.

**User Story:** As a user, I want to open Results and see how yesterday’s (and prior days’) Strong/Moderate picks performed so that I can judge the tips over time.

**Data Required:**
- Recommendation snapshots with outcomes and match result summary
- Filters: date, outcome (type/confidence deferred to v1.1)

**Decisions (locked):**
| Topic | Choice |
|-------|--------|
| Default date | Latest `snapshotDate` ≤ today (brand timezone) |
| List shape | Group by fixture |
| v1 filters | Date + outcome |
| Day hit rate | `wins / (wins + losses)`; exclude VOID / PENDING / UNSUPPORTED |
| UNSUPPORTED on UI | Show, muted; not counted in hit rate |
| Nav position | After Fixtures, before Shortlist |
| Type/confidence filters | Defer to v1.1 |
| UC-035 entry | Not required on v1 page |

**Website:**
- New nav item: **Results**
- Route: `/results`
- Default view: most recent snapshot day with settled/pending picks

**Minimum content (v1 page contract):**
- Pick list for a selected calendar day (`Europe/London`), grouped by fixture
- Per pick: type, market, confidence, outcome badge (Win / Loss / Void / Pending / Unsupported)
- Per fixture: home vs away, kickoff, league, FT scoreline when available
- Empty states: no snapshot for day; snapshot exists but all pending
- Day summary strip: wins / losses / voids / pending / unsupported / hit rate

**API Endpoints:**
- `GET /api/results/dates` — dates that have snapshots (descending)
- `GET /api/results?date=YYYY-MM-DD&outcome=` — daily dashboard (outcome optional); omit date → latest ≤ today

**Out of scope for this UC:** charts, ROI, long-window analytics (see UC-035).

**Acceptance Criteria:**
- [x] Results appears in site navigation
- [x] User can view picks for a snapshot date
- [x] Win / Loss / Void / Pending / Unsupported are clearly identifiable
- [x] Scoreline shown when completed match data exists
- [x] Timezone for “day” is `Europe/London`
- [x] Page remains usable when some picks are still PENDING

**Status:** Implemented

---

#### UC-035: Results Performance Analytics

**Goal:** Aggregate historical settled picks into hit rates by type and confidence (the analytics intent formerly drafted as UC-028).

**User Story:** As a user, I want to see how accurate each recommendation type has been over recent periods so that I know which markets are most reliable.

**Depends on:** UC-031–UC-033 (and preferably UC-034 as the host surface).

**Decisions (locked):**
| Topic | Choice |
|-------|--------|
| Hit rate | `wins / (wins + losses)` |
| Excluded from denominator | VOID, PENDING, UNSUPPORTED |
| Window basis | `snapshotDate` in `Europe/London` |
| Periods | `7d` / `30d` / `90d` / `all` |
| Minimum sample | Hit rate shown only when `wins + losses >= 10`; otherwise “Not enough data” / `enoughData=false` |
| UI | Same Results page; toggle **1 - Daily Dashboard \| 2 - Overall Performance Tracker \| 3 - Elite Pick Performance** (no new nav item) |
| ROI basis | Flat one unit per settled pick that carried odds > 1.0; unpriced picks excluded |
| Calibration basis | Settled picks bucketed by published score (`<50`, `50-59`, `60-69`, `70-79`, `80-89`, `90+`) |
| v1 out of scope | Streaks, charts, per-league |

**Statistics:**
- Overall hit rate for the period
- Hit rate by confidence (Strong vs Moderate)
- Hit rate by recommendation type (with Strong/Moderate breakdown)
- Sample size per bucket
- By-type table includes **every** `RecommendationType` (Over 1.5 / Over 2.5 included) even when the window has no snapshots yet (sample 0 / not enough data)
- **ROI, average odds and break-even rate** per bucket. Hit rate alone cannot decide whether a
  board is worth publishing: 67% at average odds 1.44 still loses money because it needs 69.4% to
  break even. `roi` is null for markets that carry no price, which is the honest answer rather
  than an implied zero.
- **Calibration bands** per type: mean published score against realized hit rate, with the gap.
  Only computed for types scored as a probability percentage — a corners or booking-points score
  is a predicted count, so comparing it to a hit rate would be meaningless.

**Why calibration is tracked:** hit rate can look respectable while an engine drifts far from
reality. The player prop boards published claims around 80% while striking at 10–19%, and no
existing metric flagged it because neither board carries a price. A reliability band comparing
claim to outcome catches that class of failure directly.

**UI:**
- View toggle on Results: **1 - Daily Dashboard** (UC-034) | **2 - Overall Performance Tracker** (UC-035) | **3 - Elite Pick Performance** (UC-037)
- On Overall Performance Tracker: period chips replace the day picker
- Overall summary strip + Strong/Moderate panels + by-type table

**API:**
```
GET /api/results/performance?period=7d|30d|90d|all
```

**Response (shape):**
```json
{
  "period": "30d",
  "fromDate": "2026-07-15",
  "toDate": "2026-08-13",
  "minSample": 10,
  "overall": { "wins": 49, "losses": 71, "voids": 8, "pending": 0, "unsupported": 0, "hitRate": 40.8, "sampleSize": 120, "enoughData": true },
  "byConfidence": {
    "STRONG": { "wins": 28, "losses": 21, "voids": 0, "pending": 0, "unsupported": 0, "hitRate": 57.1, "sampleSize": 49, "enoughData": true },
    "MODERATE": { "wins": 21, "losses": 50, "voids": 0, "pending": 0, "unsupported": 0, "hitRate": 29.6, "sampleSize": 71, "enoughData": true }
  },
  "byType": [
    {
      "type": "BTTS",
      "wins": 15, "losses": 9, "voids": 0, "pending": 0, "unsupported": 0,
      "hitRate": 62.5, "sampleSize": 24, "enoughData": true,
      "byConfidence": {
        "STRONG": { "wins": 10, "losses": 4, "voids": 0, "pending": 0, "unsupported": 0, "hitRate": 71.4, "sampleSize": 14, "enoughData": true },
        "MODERATE": { "wins": 5, "losses": 5, "voids": 0, "pending": 0, "unsupported": 0, "hitRate": 50.0, "sampleSize": 10, "enoughData": true }
      }
    }
  ]
}
```

**Acceptance Criteria:**
- [x] Stats computed only from terminal outcomes (`WIN`/`LOSS`); VOID/PENDING/UNSUPPORTED excluded from hit-rate denominator
- [x] Period filters work (`7d` / `30d` / `90d` / `all`) on `snapshotDate`
- [x] Overall + by confidence + by type returned
- [x] Minimum sample size messaging when graded count &lt; 10
- [x] Updates as new days settle (query live snapshots; no batch rebuild)
- [x] Results page hosts Performance via Daily Dashboard / Overall Performance Tracker / Elite Pick Performance toggle

**Status:** Implemented

---

#### UC-037: Elite Picks Daily Snapshot + Results Tab

**Goal:** Freeze each day’s **Elite Picks** alongside the Strong/Moderate daily snapshot (UC-031), and surface those picks with settlement outcomes on Results under a dedicated **Elite Picks** view.

**User Story:** As a user, I want to see how that day’s Elite shortlist performed so that I can track the curated top tips separately from the full Daily Dashboard and longer-run Performance.

**Depends on:** UC-031 (daily snapshot), UC-033 (settlement), UC-034 (Results host), UC-036 (Elite ranking rules).

**Scope nuance (locked):**
Live Recommendations Elite (UC-036) ranks across the **horizon** (3d/7d). Results Elite is **elite-of-the-day**: apply the same UC-036 population rules to that day’s snapshotted Strong %-style picks (fixtures kicking off that London calendar day). Cap remains top **10**, ≤1 per fixture, ≤3 BTTS.

**Population (same rules as UC-036, day-scoped):**
1. Pool = snapshotted picks for `snapshotDate` with **STRONG** confidence
2. Eligible %-style types only (UC-036 list)
3. Rank by `score` desc → lower odds → sooner kickoff
4. Dedupe ≤1 per fixture; at most **3 BTTS**; take top 10

**Persistence:**
- Nullable `eliteRank` (`1`…`10`) on `RecommendationSnapshot`
- Assigned (and cleared/reassigned) at the end of each daily snapshot upsert for that `snapshotDate`
- Settlement unchanged — Elite rows are a tagged subset of existing snapshots

**Historical days:** If a day has no `eliteRank` tags (pre-feature snapshots), Results may compute Elite on read with the same selector (display-only; optional backfill later).

**Results UI:**
- Third view toggle: **1 - Daily Dashboard** | **2 - Overall Performance Tracker** | **3 - Elite Pick Performance**
- Route: `/results?view=elite&date=YYYY-MM-DD`
- Same date navigation as Daily Dashboard
- Show Elite summary (wins / losses / voids / pending / unsupported / hit rate) + ranked pick list with outcomes / scorelines
- Empty: no Elite candidates for that day

**API:**
- Extend `GET /api/results?date=` day payload with `eliteSummary` + `eliteFixtures` (ordered by `eliteRank`)
- Per pick: include `eliteRank` when present

**Acceptance Criteria:**
- [ ] Daily snapshot tags up to 10 Elite picks with `eliteRank`
- [ ] Results has an **Elite Pick Performance** tab with date nav
- [ ] Elite tab shows that day’s Elite snapshot picks and settlement outcomes
- [ ] Hit rate uses WIN/LOSS only (same as Daily Dashboard)
- [ ] Days with no Elite candidates show a clear empty state

**Status:** Implementing

---

### iOS Native App

_Use cases for the iOS mobile application._

---

#### UC-030: [iOS App Use Case Name]

**Goal:** [What does this feature provide?]

**User Story:** As a mobile user, I want to [action] so that [benefit].

**Data Required:**
- TBD

**Screens/Components:**
- TBD

**Status:** Draft

---

## Data Model Summary

_As use cases are defined, summarize the domain entities needed here._

| Entity | Key Fields | Source | Used By |
|--------|------------|--------|---------|
| League | name, image, country, currentSeasonId | API `/league-list` (last entry in `season` array) | UC-001 |
| Team | id, name, cleanName, country, image, stadium_name, seasonId | API `/league-teams` | UC-002 |
| TeamSeasonStats | teamId, seasonId, matchesPlayed, points, position, wins, draws, losses, goals, conceded, goalDifference, + all stats | API `/league-teams?include=stats` | UC-002 |
| Fixture | id, seasonId, homeTeam, awayTeam, dateUnix, stadium, status, gameWeek, refereeID, referee | API `/league-matches` | UC-001 |
| FixtureOdds | fixtureId, odds_ft_1/x/2, odds_ft_over/under (0.5-4.5), odds_btts_yes/no | API `/league-matches` | UC-001 |
| FixturePotentials | fixtureId, btts_potential, o15/o25/o35/o45_potential, corners_potential, cards_potential, avg_potential, etc. | API `/league-matches` | UC-001 |
| Referee | id, full_name, first_name, last_name, known_as, seasonId | API `/league-referees` | UC-003 |
| RefereeStats | refereeId, seasonId, appearances, outcomes, goals, btts, penalties, cards (all fields) | API `/league-referees` | UC-003 |
| TeamRecentForm | teamId, results, goals, btts, over/under, corners, cards, fouls, cleanSheets (last 5 matches) | API `/lastx` | UC-004 |
| RecommendationSnapshot | snapshotDate, fixtureId, type, market, confidence, score, odds, outcome, eliteRank, matchResultJson, denormalized fixture/league labels | Internal engines + FootyStats results | UC-031–UC-035, UC-037 |
| CompletedMatch | fixtureId, status, FT/HT/2H goals, corners/cards, fetchedAt | FootyStats `/todays-matches`, `/match` | UC-032–UC-033 |

---

## API Sources

_Document the external APIs being used._

| API | Base URL | Auth | Notes |
|-----|----------|------|-------|
| football-data-api.com | `https://api.football-data-api.com` | API key via `key` query param | Rate limit: 1800 requests/hour |

### Endpoints Used

| Endpoint | Purpose | Example |
|----------|---------|---------|
| `/league-list` | Get supported leagues | `?key=XXX&chosen_leagues_only=true` |
| `/league-matches` | Get all matches for a season | `?key=XXX&season_id=17146` |
| `/league-teams` | Get teams with season stats | `?key=XXX&season_id=17146&include=stats` |
| `/league-referees` | Get referees with stats | `?key=XXX&season_id=17146` |
| `/lastx` | Get team last 5/6/10 form stats | `?key=XXX&team_id=93` |

### `/league-matches` Response Fields

**Basic match info:**

| Field | Description |
|-------|-------------|
| `id` | Match ID |
| `homeID` / `awayID` | Team IDs |
| `home_name` / `away_name` | Team names |
| `date_unix` | Match date/time (Unix timestamp) |
| `status` | `complete`, `incomplete`, `suspended`, `canceled` |
| `game_week` | Matchday/game week number |
| `homeGoalCount` / `awayGoalCount` | Goals scored |
| `stadium_name` | Venue |
| `competition_id` | League/competition ID |
| `refereeID` / `referee` | Match official ID and name |

**Pre-match potentials:**

| Field | Description |
|-------|-------------|
| `btts_potential` | Both teams to score likelihood |
| `o15_potential`, `o25_potential`, `o35_potential`, `o45_potential` | Over X.5 goals potential |
| `o05HT_potential`, `o15HT_potential` | Over X.5 half-time goals potential |
| `u15_potential` | Under 1.5 goals potential |
| `avg_potential` | Average total goals per match |
| `corners_potential` | Expected corners |
| `corners_o85_potential`, `corners_o95_potential`, `corners_o105_potential` | Over X.5 corners potential |
| `cards_potential` | Expected cards |
| `offsides_potential` | Expected offsides |

**Betting odds:**

| Field | Description |
|-------|-------------|
| `odds_ft_1`, `odds_ft_x`, `odds_ft_2` | 1X2 full-time odds |
| `odds_ft_over05` to `odds_ft_over45` | Over X.5 goals odds |
| `odds_ft_under05` to `odds_ft_under45` | Under X.5 goals odds |
| `odds_btts_yes`, `odds_btts_no` | Both teams to score odds |

**Query parameters:**
- `season_id` (required): The season ID from league-list
- `page`: Pagination (default ~300-500 matches per page)
- `max_per_page`: Up to 1000 matches per page
