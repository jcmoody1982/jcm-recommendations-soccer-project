# Local Development Guide

How to run and test the application locally before deploying to AWS.

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 25 |
| Maven | 3.9+ |
| API Key | football-data-api.com subscription |

---

## Quick Start

### 1. Clone and Build

```bash
git clone <repo-url>
cd jcm-recommendations-soccer-project
mvn clean install
```

### 2. Configure API Key

Create `web/src/main/resources/application-local.properties`:

```properties
# API Configuration
footystats.api.key=YOUR_API_KEY_HERE
footystats.api.base-url=https://api.football-data-api.com

# H2 Database (file-based for persistence across restarts)
spring.datasource.url=jdbc:h2:file:./data/soccer-recommendations
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console (for data inspection)
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Scheduler (disabled by default for local testing)
scheduler.enabled=false

# Logging
logging.level.com.jcm.recommendations.soccer=DEBUG
```

> **Note:** Never commit your API key. Add `application-local.properties` to `.gitignore`.

### 3. Run the Application

```bash
cd web
mvn spring-boot:run
```

The `local` profile is the default (H2, port 8080, scheduler off).  
To force it explicitly: `mvn spring-boot:run -Dspring-boot.run.profiles=local`  
Or run from IDE with profile `local` / no profile set.

### 4. Verify It's Running

```bash
curl http://localhost:8080/health
# Expected: ok:core+domain
```

---

## Local Testing Workflows

### Manual Data Sync

Admin routes require the **admin** beta password (default local: `admin`). Login first to get a session cookie:

```bash
curl -c /tmp/abg-cookies -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"password":"admin"}'

# Trigger full sync
curl -b /tmp/abg-cookies -X POST http://localhost:8080/api/admin/sync

# Sync specific league only
curl -b /tmp/abg-cookies -X POST "http://localhost:8080/api/admin/sync?seasonId=17146"
```

Site password (default local: `beta`) unlocks the UI and `/api/**` but not `/api/admin/**`.

### Inspect Data via H2 Console

Spring Boot 4 requires `spring-boot-h2console` (already in `web/pom.xml`) and `spring.h2.console.enabled=true`.

1. Restart the app after pulling dependency changes
2. Open browser: `http://localhost:8080/h2-console`
3. JDBC URL:
   - **local profile** (file DB): `jdbc:h2:file:./data/soccer-recommendations`
   - **default profile** (in-memory): `jdbc:h2:mem:soccer-recommendations`
4. Username: `sa`
5. Password: (leave blank)
6. Click Connect

**Useful queries:**
```sql
-- Check leagues
SELECT * FROM league;

-- Today's recommendation snapshots
SELECT * FROM recommendation_snapshot ORDER BY match_date_unix, type;

-- Completed match results
SELECT * FROM completed_match;

-- Check fixtures for next 7 days
SELECT * FROM fixture WHERE date_unix > UNIX_TIMESTAMP() AND date_unix < UNIX_TIMESTAMP() + (7 * 86400);

-- Check teams
SELECT * FROM team WHERE season_id = 17146;

-- Count records
SELECT 'leagues' as entity, COUNT(*) as count FROM league
UNION SELECT 'fixtures', COUNT(*) FROM fixture
UNION SELECT 'teams', COUNT(*) FROM team
UNION SELECT 'referees', COUNT(*) FROM referee;
```

### Test REST Endpoints

```bash
# List leagues
curl http://localhost:8080/api/leagues

# Get upcoming fixtures
curl http://localhost:8080/api/fixtures

# Get fixtures for specific league
curl http://localhost:8080/api/fixtures?seasonId=17146

# Get team details
curl http://localhost:8080/api/teams/93

# Get team recent form
curl http://localhost:8080/api/teams/93/form

# Get referee details
curl http://localhost:8080/api/referees/393
```

---

## Configuration Profiles

| Profile | Use Case | Database | Scheduler |
|---------|----------|----------|-----------|
| `local` | Development | H2 file | Disabled |
| `test` | Automated tests | H2 in-memory | Disabled |
| `staging` | Pre-prod testing | RDS/PostgreSQL | Enabled |
| `prod` | Production | RDS/PostgreSQL | Enabled |

---

## Running Tests Locally

### Unit Tests Only
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Specific Test Class
```bash
mvn test -Dtest=FixtureServiceTest
```

### With Coverage Report
```bash
mvn verify jacoco:report
# Report at: target/site/jacoco/index.html
```

---

## Troubleshooting

### API Key Issues

```
ERROR [FootyStatsApiClient] API returned 401 Unauthorized
```
- Verify API key is correct in `application-local.properties`
- Check API subscription is active
- Verify rate limits haven't been exceeded

### Database Lock Issues

```
ERROR Database may be already in use
```
- Stop any other running instances
- Delete `./data/soccer-recommendations.mv.db.lock` file
- Or use in-memory mode: `jdbc:h2:mem:soccer-recommendations`

### Port Already in Use

```
ERROR Web server failed to start. Port 8080 was already in use.
```
- Kill process using port: `lsof -i :8080` then `kill <PID>`
- Or change port: `-Dserver.port=8081`

---

## Local vs AWS Differences

| Aspect | Local | AWS |
|--------|-------|-----|
| Database | H2 (file or memory) | RDS PostgreSQL |
| Scheduler | Disabled (manual trigger) | Enabled (cron) |
| API Key | application-local.properties | Secrets Manager / Env var |
| Logging | Console | CloudWatch |
| URL | localhost:8080 | Load Balancer / API Gateway |

---

## Pre-Deployment Checklist

Before deploying to AWS, verify locally:

- [ ] Application starts without errors
- [ ] Manual sync completes successfully
- [ ] Data appears in H2 console
- [ ] REST endpoints return expected data
- [ ] All tests pass (`mvn verify`)
- [ ] Logs show expected INFO/DEBUG output
- [ ] No hardcoded API keys or secrets

---

## Sample Local Test Session

```bash
# 1. Start the app
cd web && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. In another terminal, login as admin then trigger sync
curl -c /tmp/abg-cookies -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"password":"admin"}'
curl -b /tmp/abg-cookies -X POST http://localhost:8080/api/admin/sync

# 3. Watch logs for progress
# INFO [DataSyncJob] Starting daily data sync job
# INFO [LeagueService] Leagues persisted: new=42, updated=0
# INFO [FixtureService] Fixtures persisted for season 17146: new=12, updated=0
# ...
# INFO [DataSyncJob] Sync completed successfully: duration=45s

# 4. Query the data
curl http://localhost:8080/api/fixtures | jq '.[:3]'

# 5. Inspect in H2 console
open http://localhost:8080/h2-console
```
