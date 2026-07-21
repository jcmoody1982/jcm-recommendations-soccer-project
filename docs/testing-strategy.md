# Testing Strategy

Testing and validation approach for the soccer recommendations system.

## Test Levels

### Level 1: Unit Tests

**Domain Entities** (`domain/src/test/`)
- Entity validation (required fields, constraints)
- Equals/hashCode contracts
- Any domain logic methods

**Services** (`core/src/test/`)
- Mock the API client and repositories
- Test business logic in isolation
- Test mapping from DTOs to entities
- Test filtering logic (e.g., fixtures within 7 days)

**API Client** (`core/src/test/`)
- Mock HTTP responses
- Test JSON deserialization
- Test error handling (timeouts, 4xx, 5xx)

---

### Level 2: Integration Tests

**Repository Tests** (`core/src/test/`)
- Use `@DataJpaTest` with H2
- Test CRUD operations
- Test custom queries
- Verify relationships (e.g., Fixture → Team)

**Service Integration Tests**
- Use `@SpringBootTest` with H2
- Test service → repository flow
- Verify data is persisted correctly

---

### Level 3: API Contract Tests

**Validate External API Responses**

Record sample responses from football-data-api.com and store as test fixtures:

```
src/test/resources/
  api-responses/
    league-list.json
    league-matches.json
    league-teams.json
    league-referees.json
    lastx.json
```

- Test that DTOs deserialize correctly from recorded responses
- Catch breaking API changes early
- Update fixtures periodically to stay current

---

### Level 4: End-to-End Tests

**Web Layer Tests** (`web/src/test/`)
- `@SpringBootTest` with `WebEnvironment.RANDOM_PORT`
- Test REST endpoints return expected data
- Test error responses (404, 400, 500)

**Full Flow Tests**
- Mock the external API (WireMock)
- Trigger data sync
- Verify data appears in database
- Verify REST endpoints serve the data

---

### Level 5: Data Validation

**Runtime Validation**
- Bean Validation (`@NotNull`, `@Size`, etc.) on entities
- Validate API responses before persisting
- Log/alert on unexpected data shapes

**Data Integrity Checks**
- Verify fixture team IDs exist in Team table
- Verify fixture refereeID exists in Referee table
- Check for orphaned records

---

## Test Coverage Goals

| Layer | Target Coverage |
|-------|-----------------|
| Domain entities | 90%+ |
| Services | 80%+ |
| API Client | 80%+ |
| Repositories | 70%+ |
| Controllers | 70%+ |

---

## Validation Checkpoints

| Checkpoint | What to Validate |
|------------|------------------|
| **After API fetch** | Response not null, expected fields present |
| **Before persist** | Required fields populated, IDs valid |
| **After persist** | Record count matches expected |
| **Daily health check** | Data freshness (last sync < 24h ago) |

---

## Test Data Strategy

| Environment | Data Source |
|-------------|-------------|
| **Unit tests** | Handcrafted test fixtures |
| **Integration tests** | H2 in-memory, seeded data |
| **Local dev** | H2 file-based, real API (or cached responses) |
| **CI/CD** | Mocked API responses (WireMock) |

---

## Test Libraries

| Library | Purpose |
|---------|---------|
| JUnit 5 | Test framework |
| Mockito | Mocking dependencies |
| AssertJ | Fluent assertions |
| WireMock | Mock external HTTP APIs |
| Testcontainers | (optional) Real DB for integration tests |

---

## Implementation Steps with Testing

| Step | Implementation Task | Testing Task |
|------|---------------------|--------------|
| 1 | Add dependencies | Add test dependencies (Mockito, AssertJ, WireMock) |
| 2 | Create domain entities | Unit tests for validation |
| 3 | Create repositories | `@DataJpaTest` integration tests |
| 4 | Build API client + DTOs | Unit tests with mock HTTP, contract tests |
| 5 | Implement services | Unit tests (mocked), integration tests |
| 6 | Add scheduler | Integration test for trigger |
| 7 | Create REST controllers | `@WebMvcTest` + E2E tests |
| 8 | Full E2E validation | WireMock + full flow test |

---

## Test Naming Convention

```
[UnitUnderTest]_[Scenario]_[ExpectedBehavior]
```

Examples:
- `FixtureService_WhenFixturesWithin7Days_ReturnsOnlyUpcoming`
- `LeagueRepository_WhenSaveLeague_PersistsSuccessfully`
- `FootyStatsApiClient_WhenApiReturns500_ThrowsApiException`

---

## CI/CD Integration

- Run unit tests on every commit
- Run integration tests on PR merge
- Run E2E tests on deployment to staging
- Generate coverage reports (JaCoCo)
- Fail build if coverage drops below threshold
