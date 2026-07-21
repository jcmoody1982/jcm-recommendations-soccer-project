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

**Status:** Draft | Defined | In Progress | Done
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

**Status:** Defined

**Next Steps:**
- [ ] Explore the fixtures endpoint to understand its structure
- [ ] Define domain entities (League, Season, Fixture, Team)
- [ ] Implement persistence layer

---

## Data Model Summary

_As use cases are defined, summarize the domain entities needed here._

| Entity | Key Fields | Source | Used By |
|--------|------------|--------|---------|
| League | name, image, country, currentSeasonId | API `/league-list` (last entry in `season` array) | UC-001 |
| Team | id, name | API `/league-matches` (`homeID`/`awayID`, `home_name`/`away_name`) | UC-001 |
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
