# FootyStats API mapping

Contract for integrating [FootyStats / Football Data API](https://footystats.org/api/documentations) into this project.

## Module ownership

| Module | Owns |
|--------|------|
| `domain` | Domain types only — no HTTP, no JSON wire DTOs, no API keys |
| `core` | API calls, response envelope/DTO parsing, sentinel cleanup, mapping to domain, recommendation use of domain types |
| `web` | App HTTP surface later; never calls FootyStats directly |

## API snapshot

- **Base URL:** `https://api.football-data-api.com`
- **Auth:** query param `key` on every request (configure via `FOOTYSTATS_API_KEY` / `footystats.api-key` — never commit secrets)
- **Envelope:** `{ success, pager, metadata, data, message }`
- **Rate limit:** `metadata.request_limit`, `metadata.request_remaining` (hourly refresh)
- **Important IDs:** `season_id`, `team_id`, `match_id`

## Endpoints (v1)

| Priority | Method / path | Purpose |
|----------|---------------|---------|
| P0 | `GET /test-call` | Key validation |
| P0 | `GET /league-list?chosen_leagues_only=true` | Subscribed competitions → seasons |
| P0 | `GET /todays-matches?date=&timezone=` | Matchday fixtures (paginate via `pager`) |
| P0 | `GET /match?match_id=` | Match detail: odds, potentials, H2H, trends |
| P0 | `GET /lastx?team_id=` | Last 5/6/10 form |
| P1 | `GET /league-teams?season_id=&include=stats` | Season team strength |
| P1 | `GET /league-tables?season_id=` | Standings |
| P2 | `GET /stats-data-btts`, `GET /stats-data-o25` | Hot BTTS / O2.5 lists |

**Paging:** follow `pager.current_page` / `pager.max_page` when aggregating.

**Sentinels:** match/team numeric placeholders `-1` and `-2` mean “unavailable” and must map to `null` in domain, not `0`.

## Team stats key names

`/league-teams?include=stats` returns ~1,065 keys per team and the names are not guessable. A wrong
key does not fail deserialisation — the field stays `null` and the engines silently substitute a
hard-coded default, so a typo looks like a plausible recommendation rather than an error.
`TeamStatsDtoMappingTest` pins every field to a captured payload
(`core/src/test/resources/footystats/league-teams-stats.json`) to catch this.

Names that are easy to get wrong:

| Meaning | Correct key | Wrong guess |
|---------|-------------|-------------|
| Matches played | `seasonMatchesPlayed_overall` / `_home` / `_away` | `matchesPlayed` |
| Goals scored | `seasonScoredNum_overall` / `_home` / `_away` | `seasonGoalsNum_home` |
| Goals conceded | `seasonConcededNum_overall` / `_home` / `_away` | — |
| Over 1.5/2.5/3.5 counts | `seasonOver15Num_overall` (etc.) | `seasonOver15_overall` |
| Goals per game | `seasonScoredAVG_home` / `seasonConcededAVG_home` | `scoredAvgHome` |
| League position | `leaguePosition_overall` | `table_position` |

Two traps worth calling out:

- `seasonGoals_overall` is an integer, but `seasonGoals_home` is an **array of goal minutes**. Use
  `seasonScoredNum_*` for goal counts so the overall and venue fields mean the same thing.
- There is no season points total in the feed, only points-per-game. `TeamStatsDto.getPoints()`
  rebuilds it as `3W + D`.

**Venue divisors:** a venue-only numerator must be divided by venue matches, never by
`seasonMatchesPlayed_overall` — that understates every home and away rate by roughly half. Go
through `RecommendationUtils.calculateMatchesAtVenue`, `calculateVenueGoalsAvg` and
`calculateVenueConcededAvg`, which prefer the feed's own per-game averages and fall back to
venue goals over venue matches.

## Domain map

| Domain type | Source |
|-------------|--------|
| `Competition`, `Season` | `/league-list` |
| `Team` | `/league-teams`, match home/away IDs |
| `Fixture`, `Scoreline`, `MatchStats` | `/todays-matches`, `/match`, `/league-matches` |
| `MatchOdds`, `PreMatchPotentials`, `HeadToHead` | `/match` |
| `TeamForm` | `/lastx` |
| `TeamSeasonStats` | `/league-teams?include=stats` (curated fields only) |
| `LeagueTable`, `TableRow` | `/league-tables` |
| `Recommendation`, `RecommendationBundle` | App-owned outputs |

## Product default

Pre-match recommendations for subscribed leagues: match-result lean, BTTS, Over/Under 2.5.
