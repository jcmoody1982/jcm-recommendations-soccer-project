# Java project specification

Contract for structure and scaffolding of this repository. Agents and humans should follow this document when generating or changing the Maven layout.

## Purpose

Define a Maven multi-module Spring Boot project that separates domain models, application logic, and the web layer, and produces a deployable Spring Boot WAR with bundled runtime dependencies.

## Coordinates

| Item | Value |
|------|--------|
| Maven `groupId` | `com.jcm.recommendations.soccer` |
| Parent `artifactId` | `jcm-recommendations-soccer-project` |
| Base package | `com.jcm.recommendations.soccer` |

Submodule packages live under the base package (for example `...domain`, `...core`, `...web`).

## Runtime and build

- **Language:** Java **25**
- **Build:** Maven multi-module (reactor)
- **Framework:** Spring Boot **4.1.x** (Spring Framework via Boot)
- **Parent POM role:** `packaging` `pom` — owns `modules`, shared properties, `dependencyManagement` / Boot BOM, and `pluginManagement`
- Prefer importing or extending Spring Boot’s dependency management so starter versions stay aligned

## Module map

Dependency direction is strictly **web → core → domain**. No reverse dependencies.

```text
jcm-recommendations-soccer-project (pom)
├── domain  (jar)
├── core    (jar)  → depends on domain
└── web     (war)  → depends on core
```

### Parent

- Aggregator only; no application source of its own
- Declares modules: `domain`, `core`, `web`
- Centralizes Java release version (`25`), encoding, and plugin versions

### `domain`

- **Role:** Domain objects and domain-centric types only
- **Packaging:** `jar`
- **No** Spring Boot application entrypoint
- Keep Spring usage minimal; prefer plain Java types unless a domain concern clearly needs a Spring annotation

### `core`

- **Role:** Main application / business logic
- **Packaging:** `jar`
- **Depends on:** `domain`
- May use Spring for services, configuration, and non-web infrastructure
- **No** web controllers or WAR packaging here

### `web`

- **Role:** Web application and HTTP surface
- **Packaging:** `war`
- **Depends on:** `core` (and transitively `domain`)
- Contains `@SpringBootApplication` entrypoint
- Extends `SpringBootServletInitializer` so the WAR can be deployed to a servlet container
- Uses Spring Web (Boot starters as appropriate)

## Source layout

Each submodule uses the standard Maven layout:

```text
src/main/java
src/main/resources
src/test/java
src/test/resources
```

Java sources under `src/main/java` and tests under `src/test/java` should mirror the same package structure.

## Packaging

| Module | Artifact | Notes |
|--------|----------|--------|
| `domain` | JAR | Library consumed by `core` |
| `core` | JAR | Library consumed by `web` |
| `web` | WAR | Spring Boot executable WAR via `spring-boot-maven-plugin` |

The **web** build must produce a working Spring Boot WAR that **includes the runtime dependencies** needed to deploy and run (Boot repackaged / executable WAR). It should be runnable with `java -jar` on the WAR and suitable for traditional WAR deployment as supported by Spring Boot.

## Testing

- **Required:** JUnit 5
- Place tests in `src/test/java` with packages mirroring production code
- Spring Boot test starters are allowed in `core` and `web` where integration testing needs them
- `domain` tests should stay lightweight (plain JUnit 5 unless there is a clear reason otherwise)

## Scaffolding checklist

When creating the tree from this spec, produce at least:

1. Root parent `pom.xml` with modules and Java 25 / Boot 4.1.x management
2. `domain/pom.xml` (`jar`) and empty package under `com.jcm.recommendations.soccer`
3. `core/pom.xml` (`jar`) depending on `domain`
4. `web/pom.xml` (`war`) depending on `core`, Boot web starter, `spring-boot-maven-plugin`, application class + `SpringBootServletInitializer`
5. Matching `src/main` and `src/test` trees in each module
6. Minimal smoke test in each module (or at least in `web`) using JUnit 5 so `mvn test` is meaningful

## Out of scope (v1)

The following may be added later and are not required by this version of the spec:

- CI pipelines
- Code formatters / static analysis (Spotless, Checkstyle, SpotBugs, etc.)
- Detailed domain package taxonomy beyond module boundaries
- Persistence, messaging, or external API choices
