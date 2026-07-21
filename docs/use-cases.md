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

### UC-001: Upcoming Fixtures for Supported Leagues

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

### UC-002: Maintain Enriched Team List

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

### UC-003: Maintain Referee List with Statistics

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
