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

**Status:** Reviewed

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

**Status:** Reviewed

---

#### UC-012: Over Corners Recommendations

**Goal:** Identify fixtures likely to have high corner counts (Over 9.5, Over 10.5).

**User Story:** As a user, I want to see which matches are likely to have many corners for over corners bets.

**Data Required:**
- Team corners won averages (home/away, season + form)
- Team corners conceded averages (home/away, season + form)
- corners_potential, corners_o85_potential, corners_o95_potential, corners_o105_potential from API

**Logic:**
```
Over Corners Score = weighted average of:
  - Home team corners won avg (home)              × 0.12
  - Away team corners won avg (away)              × 0.12
  - Home team corners conceded avg (home)         × 0.08
  - Away team corners conceded avg (away)         × 0.08
  - Home team corners won avg (last 5 home)       × 0.12
  - Away team corners won avg (last 5 away)       × 0.12
  - API corners_o95_potential                     × 0.08
  - API corners_o105_potential                    × 0.08
  - Playing style factor (home)                   × 0.05
  - Playing style factor (away)                   × 0.05
  - Defensive style factor (home)                 × 0.05
  - Defensive style factor (away)                 × 0.05
```

**Playing Style Factor (per team):**
```
Attacking Index = normalize(goals scored avg + shots on target avg)
  - High attacking (top 25% in league) = 1.2
  - Medium attacking = 1.0
  - Low attacking (bottom 25%) = 0.8
```

**Defensive Style Factor (per team):**
```
Deep Defense Index = normalize(goals conceded avg + opposition shots faced)
  - Deep defending (high conceded corners) = 1.2
  - Balanced = 1.0
  - High pressing (low conceded corners) = 0.9
```

**Recent Trend Adjustment:**
```
If last 5 corners avg > season avg by 15%+: multiply final score × 1.10
If last 5 corners avg < season avg by 15%+: multiply final score × 0.90
```

**Calculation:**
- Expected corners = (Home won + Away won + Home conceded + Away conceded) / 2
- Apply playing style and defensive style multipliers
- Apply recent trend adjustment
- Normalize to percentage based on Over 9.5 baseline

**Thresholds:**
- **Strong:** Expected corners ≥ 12 OR corners_o105_potential ≥ 70%
- **Moderate:** Expected corners 10-11.9 OR corners_o95_potential ≥ 65%
- **Weak:** Expected corners < 10

**Additional Factors:**
1. **Home/Away Split:** Home teams typically win more corners
2. **Match Context:** Close league positions may lead to more competitive play
3. **Weather/Pitch:** Note if data available (wet conditions = more corners typically)

**Output:**
- Ranked list of fixtures by expected corners
- Include: team corner stats, API potentials, expected total
- Suggested market: Over 9.5 or Over 10.5 based on expected value

**Status:** Reviewed

---

#### UC-013: Under Corners Recommendations

**Goal:** Identify fixtures likely to have low corner counts (Under 9.5, Under 8.5).

**User Story:** As a user, I want to see which matches are likely to have few corners for under corners bets.

**Data Required:**
- Team corners won averages (home/away, season + form)
- Team corners conceded averages (home/away, season + form)
- corners_potential from API

**Logic:**
```
Under Corners Score = weighted average of inverse metrics:
  - Home team corners won avg (home) inverse      × 0.12
  - Away team corners won avg (away) inverse      × 0.12
  - Home team corners conceded avg (home) inverse × 0.08
  - Away team corners conceded avg (away) inverse × 0.08
  - Home team corners won avg (last 5) inverse    × 0.12
  - Away team corners won avg (last 5) inverse    × 0.12
  - API corners_potential inverse                 × 0.16
  - Playing style factor (home) inverse           × 0.05
  - Playing style factor (away) inverse           × 0.05
  - Defensive style factor (home) inverse         × 0.05
  - Defensive style factor (away) inverse         × 0.05
```

**Playing Style Factor (inverse for under):**
```
  - Low attacking (bottom 25% in league) = 1.2 (good for under)
  - Medium attacking = 1.0
  - High attacking (top 25%) = 0.8 (bad for under)
```

**Defensive Style Factor (inverse for under):**
```
  - High pressing (low conceded corners) = 1.2 (good for under)
  - Balanced = 1.0
  - Deep defending (high conceded corners) = 0.8 (bad for under)
```

**Recent Trend Adjustment:**
```
If last 5 corners avg < season avg by 15%+: multiply final score × 1.10
If last 5 corners avg > season avg by 15%+: multiply final score × 0.90
```

**Match Context Factor:**
```
Stakes Assessment:
  - Both teams mid-table (nothing to play for) = 1.15
  - One team secured position = 1.10
  - Title race / relegation battle = 0.85 (high intensity = more corners)
  - Derby / rivalry match = 0.85

League Position Proximity:
  - Teams >10 places apart = 1.10 (likely one-sided, fewer corners)
  - Teams within 3 places = 0.95 (competitive, more corners)
```

**Calculation:**
- Expected corners = (Home won + Away won + Home conceded + Away conceded) / 2
- Lower expected = higher under score
- Apply inverse style factors
- Apply match context factor

**Thresholds:**
- **Strong:** Expected corners ≤ 8 AND corners_potential ≤ 8.5
- **Moderate:** Expected corners 8.1-9.5 OR corners_potential ≤ 9.5
- **Weak:** Expected corners > 9.5

**Additional Factors:**
1. **Home/Away Split:** Consider typical corner patterns
2. **Team Motivation:** End-of-season dead rubbers tend to have fewer corners

**Output:**
- Ranked list of fixtures by under corners score
- Include: team corner stats, expected total
- Suggested market: Under 9.5 or Under 8.5 based on expected value

**Status:** Reviewed

---

#### UC-014: Clean Sheet Recommendations

**Goal:** Identify teams likely to keep a clean sheet in upcoming fixtures.

**User Story:** As a user, I want to see which teams are likely to keep clean sheets for defensive betting markets.

**Data Required:**
- Clean sheet percentages (home/away, season + form)
- Goals conceded averages
- Opponent failed to score percentage
- Opponent goals scored averages

**Logic:**
```
Clean Sheet Score = weighted average of:
  - Team clean sheet % (season, home/away)        × 0.15
  - Team clean sheet % (last 5)                   × 0.15
  - Team goals conceded avg inverse (season)      × 0.10
  - Team goals conceded avg inverse (last 5)      × 0.10
  - Team xGA (expected goals against) inverse     × 0.15
  - Opponent failed to score % (season)           × 0.10
  - Opponent failed to score % (last 5)           × 0.10
  - Opponent xG avg inverse                       × 0.15
```
*(If xG/xGA unavailable from API, redistribute weight to goals conceded/scored)*

**xG-Based Assessment:**
```
Team Defensive xG Rating (xGA per game):
  - <0.80 xGA = Elite defense (1.25)
  - 0.80-1.10 xGA = Strong (1.10)
  - 1.10-1.40 xGA = Average (1.0)
  - >1.40 xGA = Leaky (0.80)

Opponent Attacking xG Rating (xG per game):
  - <0.80 xG = Poor creators (1.25) - good for clean sheet
  - 0.80-1.10 xG = Below average (1.10)
  - 1.10-1.50 xG = Average (1.0)
  - >1.50 xG = Strong creators (0.75) - bad for clean sheet

xG Overperformance Flag:
  - If opponent actual goals > xG by 20%+: regression likely (boost score × 1.10)
  - If team actual conceded < xGA by 20%+: regression risk (reduce score × 0.90)
```

**Defensive Strength Assessment:**
```
Goals Conceded Rating:
  - <0.75 per game = Elite (1.2)
  - 0.75-1.0 per game = Strong (1.1)
  - 1.0-1.25 per game = Average (1.0)
  - >1.25 per game = Weak (0.85)
```

**Opponent Attacking Weakness:**
```
Failed to Score Rating:
  - >40% failed to score = Poor attack (1.2)
  - 30-40% = Below average (1.1)
  - 20-30% = Average (1.0)
  - <20% = Strong attack (0.8)
```

**Recent Form Adjustment:**
```
If last 3 matches all clean sheets: × 1.15 (hot defensive streak)
If conceded in last 3 matches: × 0.90 (defensive concerns)
```

**Thresholds:**
- **Strong:** Clean Sheet Score ≥ 70%
- **Moderate:** Clean Sheet Score 50-69%
- **Weak:** Clean Sheet Score < 50%

**Additional Factors:**
1. **Home/Away Context:** Home teams keep more clean sheets typically
2. **Head-to-Head:** If available, historical clean sheets vs this opponent
3. **Key Defender Availability:** Flag if data suggests missing players
4. **Opponent Motivation:** Teams with nothing to play for may lack attacking intent

**Output:**
- Ranked list of teams by clean sheet probability
- Include: team defensive stats, opponent attacking stats, confidence level
- Pair with opponent for fixture context

**Status:** Reviewed

---

#### UC-015: First Half Goals Recommendations

**Goal:** Predict likelihood of goals in the first half (Over 0.5 HT, Over 1.5 HT).

**User Story:** As a user, I want to see which matches are likely to have first half goals.

**Data Required:**
- o05HT_potential, o15HT_potential from API
- First half goals stats (if available)
- Team goals scored/conceded timing patterns
- BTTS HT percentage

**Logic:**
```
First Half Goals Score = weighted average of:
  - API o05HT_potential                           × 0.15
  - API o15HT_potential                           × 0.10
  - Home team 1H goals scored avg                 × 0.12
  - Away team 1H goals scored avg                 × 0.12
  - Home team 1H goals conceded avg               × 0.08
  - Away team 1H goals conceded avg               × 0.08
  - BTTS HT % (home team)                         × 0.05
  - BTTS HT % (away team)                         × 0.05
  - Home team xG avg (× 0.45 for 1H proxy)        × 0.10
  - Away team xG avg (× 0.45 for 1H proxy)        × 0.10
  - Combined xGA factor                           × 0.05
```
*(If 1H-specific stats unavailable, use total goals × 0.45 as proxy)*
*(If xG unavailable, redistribute weight to goals scored/conceded)*

**xG-Based Assessment:**
```
Team 1H xG Estimate = Total xG × 0.45 (typical 1H share)

Combined xG Rating:
  - Home xG + Away xG > 3.0 = High-scoring potential (1.20)
  - 2.5-3.0 = Above average (1.10)
  - 2.0-2.5 = Average (1.0)
  - <2.0 = Low-scoring potential (0.85)

xG vs Actual Goals:
  - Both teams underperforming xG: regression = more goals likely (1.10)
  - Both teams overperforming xG: regression = fewer goals (0.90)
```

**Fast Starter Assessment:**
```
Team 1H Goals Ratio = 1H goals / Total goals
  - >55% goals in 1H = Fast starter (1.20)
  - 45-55% = Balanced (1.0)
  - <45% = Slow starter (0.85)
```

**Early Conceder Assessment:**
```
Team 1H Conceded Ratio = 1H conceded / Total conceded
  - >55% conceded in 1H = Vulnerable early (1.15)
  - 45-55% = Balanced (1.0)
  - <45% = Strong early defense (0.90)
```

**Recent Form Adjustment:**
```
If 1H goals in 4+ of last 5 matches: × 1.10
If 1H goals in 2 or fewer of last 5: × 0.90
```

**Thresholds (Over 0.5 HT):**
- **Strong:** Score ≥ 80% OR o05HT_potential ≥ 75%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65%

**Thresholds (Over 1.5 HT):**
- **Strong:** Score ≥ 70% AND o15HT_potential ≥ 60%
- **Moderate:** Score 55-69%
- **Weak:** Score < 55%

**Additional Factors:**
1. **Match Tempo:** High-pressing teams tend to score/concede early
2. **Stakes:** Must-win situations often see early aggression
3. **Weather:** Extreme conditions may slow early play

**Output:**
- Ranked list of fixtures by 1H goals probability
- Include: team 1H stats, API potentials, suggested market (O0.5 or O1.5 HT)
- Flag: "Fast starters" when both teams score >50% goals in 1H

**Status:** Reviewed

---

#### UC-016: Second Half Goals Recommendations

**Goal:** Predict likelihood of goals in the second half (Over 0.5 2H, Over 1.5 2H).

**User Story:** As a user, I want to see which matches are likely to have second half goals.

**Data Required:**
- Second half goals stats (if available)
- Team goals scored/conceded timing patterns
- 2h_cards_total as proxy for late game intensity
- xG data

**Logic:**
```
Second Half Goals Score = weighted average of:
  - Home team 2H goals scored avg                 × 0.12
  - Away team 2H goals scored avg                 × 0.12
  - Home team 2H goals conceded avg               × 0.08
  - Away team 2H goals conceded avg               × 0.08
  - Home team xG avg (× 0.55 for 2H proxy)        × 0.10
  - Away team xG avg (× 0.55 for 2H proxy)        × 0.10
  - Combined xGA factor                           × 0.05
  - Late game intensity factor                    × 0.10
  - Fitness/stamina indicator                     × 0.10
  - Match situation factor                        × 0.15
```
*(If 2H-specific stats unavailable, use total goals × 0.55 as proxy)*
*(If xG unavailable, redistribute weight to goals scored/conceded)*

**xG-Based Assessment:**
```
Team 2H xG Estimate = Total xG × 0.55 (typical 2H share - slightly higher)

Combined xG Rating:
  - Home xG + Away xG > 3.0 = High-scoring potential (1.20)
  - 2.5-3.0 = Above average (1.10)
  - 2.0-2.5 = Average (1.0)
  - <2.0 = Low-scoring potential (0.85)
```

**Late Scorer Assessment:**
```
Team 2H Goals Ratio = 2H goals / Total goals
  - >60% goals in 2H = Strong finisher (1.25)
  - 50-60% = Balanced (1.05)
  - <50% = Front-loaded (0.90)
```

**Late Conceder Assessment:**
```
Team 2H Conceded Ratio = 2H conceded / Total conceded
  - >60% conceded in 2H = Tires late (1.20)
  - 50-60% = Balanced (1.05)
  - <50% = Strong late defense (0.85)
```

**Late Game Intensity Factor:**
```
2H Cards Indicator:
  - 2h_cards_total > 1H cards = Late intensity (1.15)
  - Balanced = (1.0)
  - 2h_cards_total < 1H cards = Early intensity (0.95)
```

**Match Situation Factor:**
```
Expected Game State:
  - Evenly matched (close odds) = likely competitive 2H (1.15)
  - Heavy favorite = may ease off OR push for more (1.0)
  - Must-win for underdog = late push likely (1.20)
```

**Thresholds (Over 0.5 2H):**
- **Strong:** Score ≥ 80%
- **Moderate:** Score 65-79%
- **Weak:** Score < 65%

**Thresholds (Over 1.5 2H):**
- **Strong:** Score ≥ 70%
- **Moderate:** Score 55-69%
- **Weak:** Score < 55%

**Additional Factors:**
1. **Substitution Impact:** Fresh legs typically increase 2H tempo
2. **Historical 2H Patterns:** Some teams consistently score late
3. **Tactical Adjustments:** Teams trailing often open up in 2H

**Output:**
- Ranked list of fixtures by 2H goals probability
- Include: team 2H stats, late game intensity, suggested market
- Flag: "Strong finishers" when both teams score >55% goals in 2H

**Status:** Reviewed

---

#### UC-017: Match Result Recommendations

**Goal:** Predict 1X2 match outcomes (Home Win, Draw, Away Win).

**User Story:** As a user, I want to see predicted match results for upcoming fixtures.

**Data Required:**
- Team PPG (home/away, season + form)
- Win/Draw/Loss percentages
- Goals scored/conceded
- xG and xGA
- odds_ft_1, odds_ft_x, odds_ft_2

**Logic:**
```
For each outcome (Home Win, Draw, Away Win), calculate probability:

Home Win Probability = weighted average of:
  - Home team home win % (season)                 × 0.15
  - Home team home win % (last 5)                 × 0.15
  - Away team away loss % (season)                × 0.10
  - Away team away loss % (last 5)                × 0.10
  - Home team home PPG normalized                 × 0.10
  - Away team away PPG inverse normalized         × 0.10
  - Home team xG vs Away team xGA comparison      × 0.15
  - Goal difference comparison                    × 0.10
  - Implied probability from odds_ft_1 (sanity)   × 0.05

Away Win Probability = weighted average of:
  - Away team away win % (season)                 × 0.15
  - Away team away win % (last 5)                 × 0.15
  - Home team home loss % (season)                × 0.10
  - Home team home loss % (last 5)                × 0.10
  - Away team away PPG normalized                 × 0.10
  - Home team home PPG inverse normalized         × 0.10
  - Away team xG vs Home team xGA comparison      × 0.15
  - Goal difference comparison                    × 0.10
  - Implied probability from odds_ft_2 (sanity)   × 0.05

Draw Probability = 100% - Home Win % - Away Win %
  (with floor/ceiling adjustments, see UC-019)
```

**xG Comparison Factor:**
```
xG Dominance = Team xG - Opponent xGA
  - Dominance > 0.5 = Strong advantage (1.25)
  - Dominance 0.2-0.5 = Moderate advantage (1.10)
  - Dominance -0.2 to 0.2 = Even (1.0)
  - Dominance < -0.2 = Disadvantage (0.85)
```

**Form Momentum Factor:**
```
Last 5 Results Trend:
  - W-W-W-W-W or W-W-W-W-D = Hot streak (1.20)
  - 3+ wins in last 5 = Good form (1.10)
  - Mixed results = Neutral (1.0)
  - 3+ losses in last 5 = Poor form (0.85)
  - L-L-L-L-L = Crisis (0.70)
```

**Home Advantage Factor:**
```
League Home Win Rate Baseline:
  - Apply league-specific home advantage multiplier
  - Typical: Home +8-12% probability boost
```

**League Position Gap Factor:**
```
Position Difference = |Home position - Away position|
  - Gap ≥ 10 places: Favor higher team × 1.20
  - Gap 6-9 places: Favor higher team × 1.10
  - Gap 3-5 places: Slight favor × 1.05
  - Gap 0-2 places: No adjustment (1.0)

Apply to:
  - Higher-placed team's win probability
  - Reduce lower-placed team's win probability proportionally
```

**Motivation Factor:**
```
Team Situation Assessment:
  Title Race (top 2, within 6 pts of leader):
    - Win probability × 1.15
  
  Champions League Race (3rd-5th, within 3 pts):
    - Win probability × 1.10
  
  Relegation Battle (bottom 4, within 4 pts of safety):
    - Win probability × 1.15 (desperate = determined)
  
  Mid-table (nothing to play for):
    - Win probability × 0.95
  
  Position Secured (mathematically safe/qualified):
    - Win probability × 0.90 (may rotate/relax)
```

**Fixture Congestion Factor:**
```
Games in Last 14 Days:
  - 5+ matches: Fatigue risk × 0.90
  - 4 matches: Slight fatigue × 0.95
  - 3 or fewer: Fresh × 1.0

Comparative Advantage:
  - If opponent has 2+ more games in period: × 1.10
  - If team has 2+ more games than opponent: × 0.90
```

**Key Player Availability Factor:**
```
(When data available - flag for manual check if not)
  - Top scorer missing: Attack × 0.85
  - Starting goalkeeper missing: Defense × 0.85
  - Captain/key midfielder missing: Overall × 0.90
  - 3+ regular starters missing: Overall × 0.80
  - Full strength: No adjustment (1.0)
```

**Confidence Assessment:**
```
High Confidence: Calculated probability ≥ 60%
Medium Confidence: Calculated probability 45-59%
Low Confidence: Calculated probability < 45%
```

**Thresholds:**
- **Strong Recommendation:** Probability ≥ 55% AND value vs odds ≥ 5%
- **Moderate Recommendation:** Probability ≥ 45%
- **No Clear Recommendation:** All outcomes < 45%

**Output:**
- All three outcome probabilities for each fixture
- Recommended outcome (highest probability)
- Confidence level and value assessment vs odds
- Flag mismatches where our probability differs significantly from market

**Status:** Reviewed

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

**Thresholds:**
- **Strong Specialist:** Disparity Score ≥ 40%
- **Moderate Specialist:** Disparity Score 25-39%
- **Balanced:** Disparity Score < 25%

**Additional Factors:**
1. **Stadium Factor:** Some stadiums have notable home advantage
2. **Travel Distance:** Long away trips may affect performance
3. **Altitude/Climate:** Significant for some leagues
4. **Fan Attendance:** Correlation with home performance

**Output:**
- Ranked list of teams by home/away disparity
- Classification: Home Specialist, Away Specialist, Poor Traveler, Fortress, Balanced
- Upcoming fixture context (playing home or away)
- Recommendation: Back/Fade based on location

**Status:** Reviewed

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
- **Strong:** Draw Score ≥ 35% AND odds_ft_x ≥ 3.20
- **Moderate:** Draw Score 28-34%
- **Weak:** Draw Score < 28%

**Additional Factors:**
1. **Historical H2H:** High draw rate in previous meetings
2. **Weather Conditions:** Poor weather can lead to cagey games

**Output:**
- Ranked list of fixtures by draw probability
- Include: both team draw %, similarity scores, xG comparison
- Value flag: Compare our probability vs implied odds probability
- Flag: "Draw specialists meeting" when both teams draw > 30%

**Status:** Reviewed

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
