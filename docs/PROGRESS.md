# CommerceFlow — Progress Tracker

## Current milestone

**Milestone 1 — Product Catalog API** (`POST/GET /api/v1/products`, PostgreSQL, tested)

| Stage | Scope | Status |
|---|---|---|
| 0 | Baseline cleanup: package, Lombok removal, tracker | Done (committed) |
| 1 | PostgreSQL via Docker Compose + datasource configuration | Done: container healthy, app connects on host port 5433 |
| 2 | `Product` entity, repository, Testcontainers constraint tests | Done: 6 repository tests + `contextLoads` passing against Testcontainers PostgreSQL |
| 3 | Service + create/get/list endpoints, unit tests | Done: DTOs, exceptions, `ProductService`, `ProductController`, `ProductServiceTest` |
| 4 | Centralized error handling, API and integration tests | In progress: handlers, `ProductControllerTest`, 6 end-to-end tests incl. concurrency done; restart test and manual `curl` run pending |
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
| 2026-10-12 | `./gradlew test` | **11 passed**: `contextLoads` + 6 `ProductRepositoryTest` + 4 `ProductServiceTest` |
| 2026-10-13 | `./gradlew test --rerun` | **19 passed**: `contextLoads` + 8 `ProductControllerTest` + 6 `ProductRepositoryTest` + 4 `ProductServiceTest` |
| 2026-10-16 | `./gradlew test --rerun` | **24 passed**: + 5 `ProductApiIntegrationTest` (create/fetch via Location, duplicate SKU 409 from the real constraint, 404, 400 listing fields, non-numeric price 400) |
| 2026-10-16 | Mutation check: `saveAndFlush` -> `save` in `ProductService.create` | `duplicateSku...` e2e test **failed** (expected 409, was 500); change reverted |
| 2026-10-16 | `./gradlew test --rerun` | **25 passed**: + `concurrentCreatesWithTheSameSkuYieldExactlyOneProduct` (10 parallel POSTs, same SKU: one 201, nine 409, one row); stable over 3 runs |
| 2026-10-16 | Mutation check: check-then-insert (`findBySku` pre-check) instead of relying on the constraint | concurrency test **failed 3/3**: nine requests returned 500, one 201, no 409; change reverted |

## Architectural decisions

- Modular monolith first, package-by-feature (`com.commerceflow.product`); ADR to be written in Stage 5.
- Base package `com.commerceflow` (Initializr generated `com.commerceflow.commerceflow`).
- Lombok removed: DTOs will be records; the entity is written by hand.
- Tests get their own throwaway PostgreSQL via Testcontainers (`@ServiceConnection`), never the dev database; Testcontainers 2.0.5 versions come from the Boot BOM.
- Error mapping is split: `common/ApiExceptionHandler` (generic, extends `ResponseEntityExceptionHandler`, RFC 9457 `ProblemDetail`) knows no business package; each module maps its own exceptions (`product/ProductExceptionHandler`).
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
- Mockito service tests prove service logic only; with a mocked repository the entity id stays null, so ID generation and constraints are covered by the Testcontainers repository tests.
- A uniqueness pre-check (`findBySku` then insert) races under concurrency; the database constraint is the guarantee and the service translates its violation.
- `@WebMvcTest` loads only the MVC slice with the service mocked; it proves the HTTP contract (status codes, headers, error body), not database behaviour.
- A Gradle `test` task that is up to date reports a pass without executing; use `--rerun` when the result must be fresh.
- `save` defers the INSERT to commit, which runs after the service method returns, so the `catch` never sees the constraint violation and the client gets a 500. `saveAndFlush` forces the INSERT inside the `try`.
- `@SpringBootTest` does not roll back between tests, so end-to-end tests need unique data per test (unique SKUs).
- Check-then-insert is racy: concurrent requests all pass the pre-check, then the unique constraint rejects the losers after the pre-check, outside any translation, so clients saw 500 (observed 9 of 10 requests). The constraint is the guarantee; the pre-check only adds a race.
- JsonPath `.length()` after a filter measures the matched object, not the match count; count in Java instead.

## Known issues

- A native PostgreSQL 16 listens on `127.0.0.1:5432`, so `compose.yaml` publishes the container on host port **5433**.
- The service reports any integrity violation as a duplicate SKU; Stage 5 names the constraint so it can be matched precisely.
- Hibernate named the SKU unique constraint automatically (`ukfhmd06...`); Flyway in Stage 5 gives it a stable name.
- `ddl-auto: update` never drops anything; the old `product` table from before `@Table` may still exist in the native database.

## Next task

1. Stage 4: persistence-across-restart test and a manual `curl` run against `bootRun`.
2. Stage 5: Flyway migration (named constraints), `ddl-auto: validate`, README, ADR-0001.
