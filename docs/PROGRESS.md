# CommerceFlow — Progress Tracker

## Current milestone

**Milestone 1 — Product Catalog API** (`POST/GET /api/v1/products`, PostgreSQL, tested)

| Stage | Scope | Status |
|---|---|---|
| 0 | Baseline cleanup: package, Lombok removal, tracker | Done (committed) |
| 1 | PostgreSQL via Docker Compose + datasource configuration | Done: container healthy, app connects on host port 5433 |
| 2 | `Product` entity, repository, Testcontainers constraint tests | Done: 6 repository tests + `contextLoads` passing against Testcontainers PostgreSQL |
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
| 2026-10-11 | `./gradlew test` (native PostgreSQL 16, `Product` entity present) | **1 passed** — `contextLoads` |
| 2026-10-11 | `docker compose up -d` | `postgres:18` container reached `healthy`; published on `127.0.0.1:5433` |
| 2026-10-11 | `./gradlew test` against the Compose container | **1 passed** — `contextLoads`; `products` table created with `numeric(12,2)` price and unique `sku` |
| 2026-10-11 | `./gradlew test --tests CommerceFlowApplicationTests` with Compose DB stopped | **1 passed** against a Testcontainers `postgres:18` (random port) |
| 2026-10-11 | `./gradlew test` | **7 passed**: `contextLoads` + 6 `ProductRepositoryTest` (round trip via flush/clear, optional description, `findBySku`, duplicate SKU, null SKU, null price) |

## Architectural decisions

- Modular monolith first, package-by-feature (`com.commerceflow.product`); ADR to be written in Stage 5.
- Base package `com.commerceflow` (Initializr generated `com.commerceflow.commerceflow`).
- Lombok removed: DTOs will be records; the entity is written by hand.
- Tests get their own throwaway PostgreSQL via Testcontainers (`@ServiceConnection`), never the dev database; Testcontainers 2.0.5 versions come from the Boot BOM.
- Spring Boot 4 modular test starters (`spring-boot-starter-webmvc-test`, etc.) kept instead of declaring `spring-boot-starter-test` directly.

## Concepts covered

- Spring Boot auto-configuration fails fast: JPA on the classpath requires a `DataSource`, and with no URL and no embedded database there is no driver to choose.

- Hibernate needs a no-arg constructor because it instantiates by reflection and then populates fields; `protected` so generated proxy subclasses can call it.
- `Instant.now()` has finer precision than `timestamptz` (microseconds), so `createdAt` is truncated before persisting.
- Repository implementations come from Spring Data JPA (a proxy over `SimpleJpaRepository`), not from Hibernate.
- `save()` calls `persist` when `id == null` and `merge` otherwise, so saving the same managed instance twice is a no-op, not a second row. INSERTs run at flush, which is why constraint tests need `saveAndFlush`.
- `findById` right after `save` returns the cached instance without SQL; a real round-trip test must `flush()` and `clear()` first.
- `@DataJpaTest` loads only the JPA slice and rolls back per test; `@SpringBootTest` loads everything and is for end-to-end tests.
- `numeric(12,2)` returns scale 2, so compare prices with `isEqualByComparingTo`.

## Known issues

- A native PostgreSQL 16 listens on `127.0.0.1:5432`, so `compose.yaml` publishes the container on host port **5433**.
- Hibernate named the SKU unique constraint automatically (`ukfhmd06...`); Flyway in Stage 5 gives it a stable name.
- `ddl-auto: update` never drops anything; the old `product` table from before `@Table` may still exist in the native database.

## Next task

1. Stage 3: DTO records (`CreateProductRequest`, `ProductResponse`) and `ProductService` with Mockito unit tests.
2. Then `ProductController` and the create/get/list endpoints.
