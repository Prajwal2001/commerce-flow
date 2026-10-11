# CommerceFlow — Progress Tracker

## Current milestone

**Milestone 1 — Product Catalog API** (`POST/GET /api/v1/products`, PostgreSQL, tested)

| Stage | Scope | Status |
|---|---|---|
| 0 | Baseline cleanup: package, Lombok removal, tracker | Done (commits pending — git identity not configured) |
| 1 | PostgreSQL via Docker Compose + datasource configuration | Blocked: Docker not available in WSL |
| 2 | `Product` entity, repository, Testcontainers constraint tests | Not started |
| 3 | Service + create/get/list endpoints, unit tests | Not started |
| 4 | Centralized error handling, API and integration tests | Not started |
| 5 | Flyway migration, remove `ddl-auto`, README, ADR-0001 | Not started |

## Stack (verified on this machine)

- JDK: Temurin 25.0.1; Gradle toolchain `JavaLanguageVersion.of(25)`
- Spring Boot 4.1.1, Gradle wrapper 9.7.1 (Groovy DSL)
- Hibernate ORM 7.4.5, JUnit Jupiter 6.0.3, Mockito 5.23.0 (managed by Spring Boot)

## Verified results

| Date | Command | Result |
|---|---|---|
| 2026-10-11 | `./gradlew clean compileJava compileTestJava` | BUILD SUCCESSFUL |
| 2026-10-11 | `./gradlew dependencies --configuration testRuntimeClasspath` | `spring-boot-starter-test:4.1.1` present transitively |
| 2026-10-11 | `./gradlew test` | **1 failed** — `contextLoads`: `Failed to determine a suitable driver class` (expected: no datasource configured yet; fixed in Stage 1/2) |

No tests have passed yet.

## Architectural decisions

- Modular monolith first, package-by-feature (`com.commerceflow.product`); ADR to be written in Stage 5.
- Base package `com.commerceflow` (Initializr generated `com.commerceflow.commerceflow`).
- Lombok removed: DTOs will be records; the entity is written by hand.
- Spring Boot 4 modular test starters (`spring-boot-starter-webmvc-test`, etc.) kept instead of declaring `spring-boot-starter-test` directly.

## Concepts covered

- Spring Boot auto-configuration fails fast: JPA on the classpath requires a `DataSource`, and with no URL and no embedded database there is no driver to choose.

## Known issues

- Docker is not reachable from this WSL distro (enable Docker Desktop → Settings → Resources → WSL Integration).
- Git `user.name` / `user.email` are not set, so nothing is committed yet. The untouched Initializr output is staged in the index; Stage 0 changes are unstaged.

## Next task

1. Set git identity and make the two Stage 0 commits.
2. Enable Docker in WSL; confirm `docker compose version`.
3. Stage 1: `compose.yaml` + datasource configuration.
