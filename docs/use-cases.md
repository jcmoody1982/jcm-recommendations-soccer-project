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
- **Fixture**: id, league/season, home team, away team, match date/time, venue, status
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

**Status:** Draft

---

## Data Model Summary

_As use cases are defined, summarize the domain entities needed here._

| Entity | Key Fields | Source | Used By |
|--------|------------|--------|---------|
| League | name, image, country, currentSeasonId | API `/league-list` (last entry in `season` array) | UC-001 |
| Team | id, name, cleanName, country, image, stadium_name, seasonId | API `/league-teams` | UC-002 |
| TeamSeasonStats | teamId, seasonId, matchesPlayed, points, position, wins, draws, losses, goals, conceded, goalDifference, + all stats | API `/league-teams?include=stats` | UC-002 |
| Fixture | id, seasonId, homeTeam, awayTeam, dateUnix, stadium, status, gameWeek | API `/league-matches` | UC-001 |

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

### `/league-matches` Response Fields

Key fields from match objects:

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

Query parameters:
- `season_id` (required): The season ID from league-list
- `page`: Pagination (default ~300-500 matches per page)
- `max_per_page`: Up to 1000 matches per page
