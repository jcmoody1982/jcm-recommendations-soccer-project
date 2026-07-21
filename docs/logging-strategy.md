# Logging Strategy

Logging approach for observability, debugging, and operational monitoring.

## Framework

- **SLF4J** — logging facade (included with Spring Boot)
- **Logback** — logging implementation (Spring Boot default)
- **Structured logging** — key-value pairs for easy parsing/searching

---

## Log Levels

| Level | When to Use |
|-------|-------------|
| `ERROR` | Operation failed, requires attention, data may be lost/incomplete |
| `WARN` | Unexpected but recoverable, degraded functionality, retry needed |
| `INFO` | Key business events, operation start/complete, milestones |
| `DEBUG` | Detailed flow information, useful for development/troubleshooting |
| `TRACE` | Very detailed, typically data dumps, rarely used in production |

---

## Scheduler Logging Requirements

### Sync Job Start

```
INFO  [DataSyncJob] Starting daily data sync job
INFO  [DataSyncJob] Sync job parameters: leagues=42, lookAheadDays=7
```

### Per-Operation Logging

```
INFO  [LeagueService] Fetching league list from API
INFO  [LeagueService] League list fetched successfully: count=42
INFO  [LeagueService] Leagues persisted: new=0, updated=2, unchanged=40

INFO  [FixtureService] Fetching fixtures for season: seasonId=17146, league=England Premier League
INFO  [FixtureService] Fixtures fetched: total=380, upcoming7Days=12
INFO  [FixtureService] Fixtures persisted for season 17146: new=2, updated=10, unchanged=0

INFO  [TeamService] Fetching teams for season: seasonId=17146
INFO  [TeamService] Teams fetched: count=20
INFO  [TeamService] Teams persisted for season 17146: new=0, updated=20

INFO  [RefereeService] Fetching referees for season: seasonId=17146
INFO  [RefereeService] Referees fetched: count=18
INFO  [RefereeService] Referees persisted for season 17146: new=1, updated=17

INFO  [TeamFormService] Fetching recent form for team: teamId=93, name=Manchester City
INFO  [TeamFormService] Recent form fetched for team 93
```

### Sync Job Completion

```
INFO  [DataSyncJob] Daily data sync completed successfully
INFO  [DataSyncJob] Sync summary: duration=45s, leagues=42, fixtures=156, teams=840, referees=420, apiCalls=167
```

### Progress Logging (for long operations)

```
INFO  [DataSyncJob] Progress: 10/42 leagues processed (24%)
INFO  [DataSyncJob] Progress: 20/42 leagues processed (48%)
INFO  [DataSyncJob] Progress: 30/42 leagues processed (71%)
INFO  [DataSyncJob] Progress: 42/42 leagues processed (100%)
```

---

## Error Logging

### API Errors

```
ERROR [FootyStatsApiClient] API call failed: endpoint=/league-matches, seasonId=17146, status=500, message=Internal Server Error
ERROR [FootyStatsApiClient] API call failed after 3 retries: endpoint=/league-matches, seasonId=17146
WARN  [FixtureService] Skipping season 17146 due to API error, will retry next sync
```

### Data Validation Errors

```
WARN  [FixtureService] Fixture missing required field: fixtureId=12345, missingField=homeID
WARN  [FixtureService] Skipping invalid fixture: fixtureId=12345
ERROR [FixtureService] Too many invalid fixtures in season 17146: invalid=50, total=380, threshold=10%
```

### Database Errors

```
ERROR [LeagueRepository] Failed to persist league: name=England Premier League, error=ConstraintViolationException
ERROR [DataSyncJob] Sync job failed: error=DataAccessException, message=Could not acquire connection
```

### Partial Failure

```
WARN  [DataSyncJob] Sync completed with errors: successful=40, failed=2, failedSeasons=[17146, 17148]
```

---

## Structured Log Format

Use key-value pairs for searchability:

```
INFO  [FixtureService] event=fixtures_fetched seasonId=17146 league="England Premier League" total=380 upcoming=12 duration=1.2s
INFO  [FixtureService] event=fixtures_persisted seasonId=17146 new=2 updated=10 unchanged=0
ERROR [FootyStatsApiClient] event=api_error endpoint=/league-matches seasonId=17146 status=500 retryCount=3
```

---

## What to Log

### Always Log (INFO)

| Event | Key Data |
|-------|----------|
| Sync job start | timestamp, parameters |
| Sync job complete | duration, counts, success/failure |
| API call complete | endpoint, seasonId, record count, duration |
| Data persisted | entity type, new/updated/unchanged counts |
| Scheduler triggered | job name, trigger time |

### Log on Error/Warning

| Event | Key Data |
|-------|----------|
| API failure | endpoint, status code, error message, retry count |
| Validation failure | entity type, ID, field, reason |
| Partial failure | success count, failure count, failed IDs |
| Rate limit approached | current usage, limit, reset time |
| Timeout | operation, duration, timeout threshold |

### Debug Only

| Event | Key Data |
|-------|----------|
| API request details | full URL, headers (not API key) |
| API response body | truncated if large |
| Entity mapping | before/after transformation |
| SQL queries | via Hibernate logging |

---

## Log Configuration

### `application.properties`

```properties
# Root logging level
logging.level.root=INFO

# Application logging
logging.level.com.jcm.recommendations.soccer=INFO

# More verbose for specific packages during development
logging.level.com.jcm.recommendations.soccer.core.client=DEBUG

# SQL logging (development only)
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# JSON format for production (optional)
# logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### Production Recommendations

```properties
logging.level.root=WARN
logging.level.com.jcm.recommendations.soccer=INFO
logging.level.org.hibernate.SQL=WARN
```

---

## Implementation Pattern

```java
@Service
@Slf4j  // Lombok annotation for logger
public class FixtureService {

    public void syncFixtures(Long seasonId, String leagueName) {
        log.info("Fetching fixtures for season: seasonId={}, league={}", seasonId, leagueName);
        
        try {
            List<Fixture> fixtures = apiClient.fetchFixtures(seasonId);
            log.info("Fixtures fetched: seasonId={}, total={}", seasonId, fixtures.size());
            
            SyncResult result = persistFixtures(fixtures);
            log.info("Fixtures persisted: seasonId={}, new={}, updated={}, unchanged={}", 
                     seasonId, result.newCount(), result.updatedCount(), result.unchangedCount());
                     
        } catch (ApiException e) {
            log.error("Failed to fetch fixtures: seasonId={}, error={}", seasonId, e.getMessage(), e);
            throw e;
        }
    }
}
```

---

## Monitoring & Alerting

| Metric | Alert Threshold |
|--------|-----------------|
| Sync job duration | > 5 minutes |
| API error rate | > 5% of calls |
| Failed syncs | > 2 consecutive |
| Data freshness | Last sync > 25 hours ago |

---

## Log Retention

| Environment | Retention |
|-------------|-----------|
| Development | 1 day |
| Staging | 7 days |
| Production | 30 days (or per compliance) |
