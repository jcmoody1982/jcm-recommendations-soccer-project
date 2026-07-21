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
- Team BTTS percentage (season + recent form)
- Goals scored/conceded averages
- Failed to score percentage
- BTTS potential from API

**Logic:**
```
BTTS Score = weighted average of:
  - Home team BTTS % (season)           × 0.15
  - Away team BTTS % (season)           × 0.15
  - Home team BTTS % (last 5)           × 0.20
  - Away team BTTS % (last 5)           × 0.20
  - Home team "failed to score" inverse × 0.10
  - Away team "failed to score" inverse × 0.10
  - API btts_potential                  × 0.10
```

**Thresholds:**
- **Strong:** BTTS Score ≥ 80%
- **Moderate:** BTTS Score 65-79%
- **Weak:** BTTS Score < 65%

**Additional Filters:**
- Both teams must have scored in 50%+ of their matches
- Neither team's "failed to score" rate > 40%

**Output:**
- Ranked list of fixtures by BTTS score
- Include: fixture details, both team stats, confidence level

**Status:** Reviewed

---

#### UC-006: Over Goals Recommendations

**Goal:** Identify fixtures likely to be high-scoring (Over 2.5, Over 3.5).

**User Story:** As a user, I want to see which matches are likely to have many goals so I can bet on over goals markets.

**Data Required:**
- Goals scored/conceded averages (season + form)
- Over 2.5/3.5 percentages
- o25_potential, o35_potential from API

**Logic:**
```
Over Goals Score = weighted average of:
  - Home team goals scored avg (season)      × 0.10
  - Away team goals scored avg (season)      × 0.10
  - Home team goals conceded avg (season)    × 0.10
  - Away team goals conceded avg (season)    × 0.10
  - Home team goals scored avg (last 5)      × 0.15
  - Away team goals scored avg (last 5)      × 0.15
  - Home team Over 2.5 % (season)            × 0.10
  - Away team Over 2.5 % (season)            × 0.10
  - API o25_potential                        × 0.10
```

**Calculation:**
- Convert to expected goals: (Home scored + Away scored + Home conceded + Away conceded) / 2
- Normalize to percentage based on historical O2.5 rates

**Thresholds:**
- **Strong:** Score ≥ 80%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65%

**Additional Filters:**
- Combined goals average ≥ 2.5 per match
- At least one team with Over 2.5 rate > 50%

**Output:**
- Ranked list of fixtures by over goals score
- Include: expected goals, team averages, confidence level

**Status:** Reviewed

---

#### UC-007: Under Goals Recommendations

**Goal:** Identify fixtures likely to be low-scoring (Under 2.5, Under 1.5).

**User Story:** As a user, I want to see which matches are likely to have few goals so I can bet on under goals markets.

**Data Required:**
- Goals scored/conceded averages
- Under 2.5/1.5 percentages
- Clean sheet percentages
- Defensive strength metrics

**Logic:**
```
Under Goals Score = weighted average of:
  - Home team goals scored avg (season) inverse      × 0.10
  - Away team goals scored avg (season) inverse      × 0.10
  - Home team goals conceded avg (season) inverse    × 0.10
  - Away team goals conceded avg (season) inverse    × 0.10
  - Home team goals scored avg (last 5) inverse      × 0.15
  - Away team goals scored avg (last 5) inverse      × 0.15
  - Home team clean sheet % (season)                 × 0.10
  - Away team clean sheet % (season)                 × 0.10
  - API u15_potential                                × 0.10
```

**Calculation:**
- Inverse = lower goals = higher score
- Factor in clean sheet rates and failed to score rates

**Thresholds:**
- **Strong:** Score ≥ 80%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65%

**Additional Filters:**
- Combined goals average ≤ 2.5 per match
- At least one team with Under 2.5 rate > 50%

**Output:**
- Ranked list of fixtures by under goals score
- Include: expected goals, defensive stats, confidence level

**Status:** Reviewed

---

#### UC-008: Booking Points Recommendations

**Goal:** Predict total booking points for a fixture (Yellow=10, Red=25).

**User Story:** As a user, I want to see expected booking points for matches so I can bet on cards markets.

**Data Required:**
- Team cards averages (home/away)
- Referee cards per match stats
- Referee yellow/red card tendencies

**Logic:**
```
Expected Booking Points = sum of:
  - Home team cards avg per match (home) × 10      × 0.20
  - Away team cards avg per match (away) × 10     × 0.20
  - Referee cards per match avg × 10              × 0.25
  - Home team red card rate × 25                  × 0.05
  - Away team red card rate × 25                  × 0.05
  - Referee reliability factor                    × 0.10
    (appearances ≥ 10 = 1.0, 5-9 = 0.8, <5 = 0.5)
  - Match intensity factor                        × 0.15
    (derby/rivalry = 1.5, same league position ±3 = 1.2, normal = 1.0)
```

**Calculation:**
- Base expected points from team card averages
- Adjust heavily based on referee tendencies
- Scale by referee data reliability
- Boost for high-intensity matchups (derbies, close standings)

**Thresholds (for Over/Under 40 booking points):**
- **Strong Over:** Expected ≥ 50 points
- **Moderate Over:** Expected 40-49 points
- **Moderate Under:** Expected 30-39 points
- **Strong Under:** Expected < 30 points

**Output:**
- Ranked list by expected booking points
- Include: team card stats, referee stats, intensity flag, confidence level

**Status:** Reviewed

---

#### UC-009: Value Bet Recommendations

**Goal:** Flag fixtures where calculated probability differs significantly from bookmaker odds.

**User Story:** As a user, I want to find bets where the odds offer value compared to statistical probability.

**Data Required:**
- Calculated probabilities from other use cases
- Bookmaker odds from API
- Implied probability from odds

**Logic:**
```
For each market (BTTS, Over 2.5, Match Result, etc.):

1. Calculate implied probability from bookmaker odds:
   Implied % = 1 / decimal_odds × 100

2. Get our calculated probability from relevant use case:
   - BTTS: UC-005 score
   - Over Goals: UC-006 score
   - Under Goals: UC-007 score
   - Match Result: UC-017 score

3. Calculate value:
   Value % = Our Probability - Implied Probability

4. Calculate expected value (EV):
   EV = (Our Probability × (odds - 1)) - (1 - Our Probability)
```

**Thresholds:**
- **Strong Value:** Value % ≥ 15% AND EV ≥ 0.10
- **Moderate Value:** Value % ≥ 10% AND EV ≥ 0.05
- **No Value:** Value % < 10% OR EV < 0.05

**Additional Factors:**
- Confidence weight from source use case (Strong/Moderate/Weak)
- Minimum odds threshold (e.g., odds ≥ 1.50) to avoid tiny margins

**Output:**
- Ranked list by EV or Value %
- Include: market, our probability, implied probability, odds, EV, confidence

**Status:** Reviewed

---

#### UC-010: Winning Form Mismatch Recommendations

**Goal:** Identify teams whose recent form is significantly better than their season average suggests.

**User Story:** As a user, I want to spot teams on hot streaks that may be undervalued by the market.

**Data Required:**
- Season PPG, goals scored avg, win percentage, clean sheet %
- Recent form (last 5) PPG, goals scored avg, win percentage, clean sheet %
- Home/away splits for both season and recent form
- Upcoming fixture location (home/away)

**Logic:**
```
Winning Mismatch Score = weighted average of deltas:
  - PPG delta: (Last 5 PPG - Season PPG) / Season PPG           × 0.30
  - Goals delta: (Last 5 goals avg - Season goals avg)          × 0.25
  - Wins delta: (Last 5 win % - Season win %)                   × 0.25
  - Clean sheets delta: (Last 5 CS % - Season CS %)             × 0.20
```

**Calculation:**
- Positive delta = team performing BETTER recently
- Normalize each delta to comparable scale (percentage improvement)
- For upcoming home fixture: weight home splits at 1.25×
- For upcoming away fixture: weight away splits at 1.25×

**Thresholds:**
- **Strong Mismatch:** Score ≥ 25% improvement
- **Moderate Mismatch:** Score 15-24% improvement
- **No Mismatch:** Score < 15% improvement

**Additional Factors:**
1. **Minimum Sample Size:** At least 5 matches in recent form window
2. **Home/Away Context:** Apply location-based weighting for upcoming fixture
3. **Streak Bonus:** Add +5% if team has won 3+ consecutive matches
4. **Quality Opposition Indicator:** Flag if recent opponents were bottom-half teams (potential false signal)
5. **Scoring Trend:** Note if goals per game increasing over last 5 (momentum indicator)
6. **Defensive Trend:** Note if goals conceded decreasing over last 5 (solidity indicator)
7. **Goals vs Performance:** Flag if actual goals significantly > expected from shots (regression risk)
8. **Fixture Difficulty:** Compare average league position of recent opponents vs season average

**Output:**
- Ranked list of teams by mismatch score
- Include: season stats, recent form stats, delta percentages
- Include: win streak status, opponent quality flag
- Flag: "Hot streak - potentially undervalued"

**Status:** Draft

---

#### UC-011: Losing Form Mismatch Recommendations

**Goal:** Identify teams whose recent form is significantly worse than their season average suggests.

**User Story:** As a user, I want to spot teams on cold streaks that may be overvalued by the market.

**Data Required:**
- Season PPG, goals scored avg, win percentage, clean sheet %
- Recent form (last 5) PPG, goals scored avg, win percentage, clean sheet %
- Home/away splits for both season and recent form
- Upcoming fixture location (home/away)

**Logic:**
```
Losing Mismatch Score = weighted average of negative deltas:
  - PPG delta: (Season PPG - Last 5 PPG) / Season PPG           × 0.30
  - Goals delta: (Season goals avg - Last 5 goals avg)          × 0.25
  - Wins delta: (Season win % - Last 5 win %)                   × 0.25
  - Clean sheets delta: (Season CS % - Last 5 CS %)             × 0.20
```

**Calculation:**
- Positive delta = team performing WORSE recently
- Normalize each delta to comparable scale (percentage decline)
- For upcoming home fixture: weight home splits at 1.25×
- For upcoming away fixture: weight away splits at 1.25×

**Thresholds:**
- **Strong Mismatch:** Score ≥ 25% decline
- **Moderate Mismatch:** Score 15-24% decline
- **No Mismatch:** Score < 15% decline

**Additional Factors:**
1. **Minimum Sample Size:** At least 5 matches in recent form window
2. **Home/Away Context:** Apply location-based weighting for upcoming fixture
3. **Losing Streak Indicator:** Add +5% if team has lost 3+ consecutive matches
4. **Quality Opposition Indicator:** Flag if recent opponents were top-half teams (not necessarily a crisis)
5. **Scoring Drought:** Note if goals per game decreasing over last 5 (attacking confidence issue)
6. **Defensive Collapse:** Note if goals conceded increasing over last 5 (structural problem)
7. **Key Metrics Divergence:** Flag if multiple metrics declining simultaneously (systemic issue)
8. **Fixture Difficulty:** Compare average league position of recent opponents vs season average
9. **Injury/Suspension Context:** Note if decline coincides with missing key players (temporary vs permanent)

**Output:**
- Ranked list of teams by mismatch score
- Include: season stats, recent form stats, delta percentages
- Include: losing streak status, opponent quality flag
- Flag: "Cold streak - potentially overvalued"

**Status:** Draft

---

#### UC-012: Over Corners Recommendations

**Goal:** Identify fixtures likely to have high corner counts.

**User Story:** As a user, I want to see which matches are likely to have many corners for over corners bets.

**Data Required:**
- Team corners averages (for/against, home/away)
- corners_potential from API

**Logic:**
- TBD

**Status:** Draft

---

#### UC-013: Under Corners Recommendations

**Goal:** Identify fixtures likely to have low corner counts.

**User Story:** As a user, I want to see which matches are likely to have few corners for under corners bets.

**Data Required:**
- Team corners averages (for/against, home/away)
- corners_potential from API

**Logic:**
- TBD

**Status:** Draft

---

#### UC-014: Clean Sheet Recommendations

**Goal:** Identify teams likely to keep a clean sheet in upcoming fixtures.

**User Story:** As a user, I want to see which teams are likely to keep clean sheets for defensive betting markets.

**Data Required:**
- Clean sheet percentages (home/away)
- Goals conceded averages
- Opponent failed to score percentage

**Logic:**
- TBD

**Status:** Draft

---

#### UC-015: First Half Goals Recommendations

**Goal:** Predict likelihood of goals in the first half (Over 0.5 HT, Over 1.5 HT).

**User Story:** As a user, I want to see which matches are likely to have first half goals.

**Data Required:**
- o05HT_potential, o15HT_potential from API
- First half goals stats if available

**Logic:**
- TBD

**Status:** Draft

---

#### UC-016: Second Half Goals Recommendations

**Goal:** Predict likelihood of goals in the second half.

**User Story:** As a user, I want to see which matches are likely to have second half goals.

**Data Required:**
- Second half goals stats
- Teams that score/concede late
- 2h_cards_total as proxy for late game intensity

**Logic:**
- TBD

**Status:** Draft

---

#### UC-017: Match Result Recommendations

**Goal:** Predict 1X2 match outcomes (Home Win, Draw, Away Win).

**User Story:** As a user, I want to see predicted match results for upcoming fixtures.

**Data Required:**
- Team PPG (home/away)
- Recent form results
- Head-to-head (if available)
- odds_ft_1, odds_ft_x, odds_ft_2

**Logic:**
- TBD

**Status:** Draft

---

#### UC-018: Home/Away Recommendations

**Goal:** Identify teams with significant performance gaps between home and away matches.

**User Story:** As a user, I want to spot teams that are much stronger at home or away to inform my bets.

**Data Required:**
- Home vs away PPG comparison
- Home vs away goals scored/conceded
- Home vs away win percentages

**Logic:**
- TBD

**Status:** Draft

---

#### UC-019: Draw Recommendations

**Goal:** Identify fixtures likely to end in a draw.

**User Story:** As a user, I want to find matches with high draw probability for draw betting.

**Data Required:**
- Team draw percentages
- Evenly matched stats comparison
- odds_ft_x from API

**Logic:**
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
