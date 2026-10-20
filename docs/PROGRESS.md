# CommerceFlow — Progress Tracker

## Current milestone

**Milestone 1 — Product Catalog API** (`POST/GET /api/v1/products`, PostgreSQL, tested)

| Stage | Scope | Status |
|---|---|---|
| 0 | Baseline cleanup: package, Lombok removal, tracker | Done (committed) |
| 1 | PostgreSQL via Docker Compose + datasource configuration | Done: container healthy, app connects on host port 5433 |
| 2 | `Product` entity, repository, Testcontainers constraint tests | Done: 6 repository tests + `contextLoads` passing against Testcontainers PostgreSQL |
| 3 | Service + create/get/list endpoints, unit tests | Done: DTOs, exceptions, `ProductService`, `ProductController`, `ProductServiceTest` |
| 4 | Centralized error handling, API and integration tests | Done: handlers, 8 controller tests, 7 end-to-end tests incl. concurrency; manual `bootRun` + `curl` and restart check performed |
| 5 | Flyway migration, remove `ddl-auto`, README, ADR-0001 | Done: Flyway `V1`, `validate`, constraint-name matching, dev volume reset and manual `bootRun` check, README, ADR-0001 |

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
| 2026-10-16 | Manual: `docker compose up -d`, `./gradlew bootRun` (started in ~4 s), `curl` | 201 + `Location`; 409 duplicate; 400 invalid body, malformed JSON and bad UUID; 404 unknown id; 200 list |
| 2026-10-16 | Manual restart: stop app, `docker compose restart postgres`, `bootRun` again, `GET` earlier product | Same id, fields and `createdAt` returned: data persisted across restart of both app and database |
| 2026-10-16 | `./gradlew test --rerun` after price normalization | **26 passed** (added `priceIsReturnedWithTwoDecimalPlacesByCreateAndGetAlike`; it failed first with `expected: 2 but was: 1`) |
| 2026-10-18 | Wrote 4 constraint-name tests first, ran `ProductServiceTest` + `ProductRepositoryTest` before any fix | **3 failed as predicted** (zero price accepted, constraint not named `uq_products_sku`, other violations reported as duplicates) |
| 2026-10-18 | `./gradlew test --rerun` after Flyway `V1` migration, `ddl-auto: validate`, named-constraint matching | **29 passed** (1 + 8 controller + 5 service + 8 repository + 7 integration) |
| 2026-10-18 | `docker compose down -v` + `up -d --wait` (dev DB was already empty), `bootRun` | Flyway applied `V1` to an empty schema, `validate` passed, started in 3.9 s; `\d products` shows `uq_products_sku` and `ck_products_price_positive`; `curl`: 201 (price `79.90`), 409, 400, list 200 |
| 2026-10-18 | Experiment: migration `numeric(10,2)` vs entity `precision = 12`, ran `contextLoads` | Startup **succeeded**: `validate` checks column types, not precision/scale. Migration restored |

## Architectural decisions

- Modular monolith first, package-by-feature (`com.commerceflow.product`); see [ADR-0001](adr/0001-modular-monolith-first.md).
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
- A manual run found POST returned `79.9` while GET returned `79.90`: the response echoed the in-memory `BigDecimal` but reads return the `numeric(12,2)` value. `Product` now normalizes price to scale 2 (`RoundingMode.UNNECESSARY`, so excess decimals fail loudly instead of being rounded; request validation rejects them earlier).
- `pkill -f <pattern>` run from a shell whose own command line contains the pattern kills that shell; use a bracket pattern such as `Foo[A]pp`.

## Known issues

- A native PostgreSQL 16 listens on `127.0.0.1:5432`, so `compose.yaml` publishes the container on host port **5433**.
- The old `product` table from before `@Table` may still exist in the native PostgreSQL on 5432 (not used by the app any more).
- `ddl-auto: validate` does not compare precision, scale or length; migrations need review and the real-schema tests are the safety net.
- HTTP response body for a non-SKU integrity violation (500) is not covered by a test.

## Concepts covered in Stage 5 (add to the list above)

- Spring wraps Hibernate's `ConstraintViolationException` (which carries the constraint name) inside `DataIntegrityViolationException`; match on the name, not the message, walking the cause chain.
- Name constraints in the migration and treat the names as a contract with the Java code.
- `ddl-auto: validate` checks tables, columns and column types, not precision, scale or length (verified by experiment).
- Flyway filenames need `V<version>__<description>.sql` with two underscores.

## Milestone 2 (in progress, branch `feature/ci`, stacked on `feature/starting-product`)

| Step | Status |
|---|---|
| CI workflow (`.github/workflows/ci.yml`: checkout, Temurin 25, Gradle cache, `./gradlew clean test`, test report on failure) | Written. Locally: YAML parses, and `./gradlew clean test --no-daemon` passed (29 tests, 1m 6s). **Not yet run on GitHub**: no remote is configured |
| Pagination and sorting | Done: `GET /api/v1/products?page&size&sort` returns a `PageResponse` envelope; size 1 to 100, whitelisted sort fields, bad values are 400 (`Invalid sort` title). Tests written first (10 red against a stub), then green; mutation check (whitelist disabled) failed exactly the two sort tests. Breaking change: the list is no longer a plain array |
| OpenAPI documentation | Done: springdoc 3.1.1 (built against Boot 4.1.0, works on 4.1.1). Swagger UI at `/swagger-ui/index.html`. Zero-annotation output documented POST as `200` with no errors; annotations add 201/400/404/409 with `application/problem+json` + `ProblemDetail`. 6 tests written first (5 red), then green; mutation (409 renamed to 418) failed exactly 2 tests. Full suite: 45 passed |
| Update / delete | Done (without versioning): `PUT /{id}` replaces name/description/price (SKU immutable), `DELETE /{id}` is a hard delete returning 204. 16 tests written first (14 behaviour + 2 documentation), all red against stubs, then green. Full suite: 61 passed. Dirty checking means `update` needs no `save`. Review found `DELETE` returning 200 instead of 204 (void controller method) |
| Optimistic locking (`@Version`, Flyway V2, version in request/response, 409 on stale) | Not started |

## Next task

Confirm the first CI run on GitHub is green (not yet reported); then optimistic locking for updates (V2 migration, two-threads-one-409 test).
