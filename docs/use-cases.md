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
- **League/Competition**: id, name, country, (logo?)
- **Fixture**: id, league, home team, away team, match date/time, venue, status
- **Team**: id, name, (crest/logo?)

**API Source(s):** TBD — need to select a football data API (e.g., football-data.org, API-Football)

**Behavior:**
- Pull upcoming fixtures (next N matches or next X days) for each supported league
- Refresh data on a daily schedule
- Store fixtures locally so the app can display them without hitting the API on every request

**Status:** Draft

**Open Questions:**
- Which leagues are "supported"? (e.g., Premier League, La Liga, Serie A, Bundesliga, Ligue 1?)
- How far ahead should fixtures be fetched? (next matchday? next 7 days? next 30 days?)
- Which API will be used?

---

## Data Model Summary

_As use cases are defined, summarize the domain entities needed here._

| Entity | Key Fields | Source | Used By |
|--------|------------|--------|---------|
| League | id, name, country | API | UC-001 |
| Team | id, name | API | UC-001 |
| Fixture | id, league, homeTeam, awayTeam, matchDateTime, venue, status | API | UC-001 |

---

## API Sources

_Document the external APIs being used._

| API | Base URL | Auth | Notes |
|-----|----------|------|-------|
| | | | |
