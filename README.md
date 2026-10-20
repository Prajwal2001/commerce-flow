# CommerceFlow

A Java e-commerce backend, built milestone by milestone as a modular monolith that can later be split into services.
**Current milestone: 1 — Product Catalog API.** Progress and verified results are tracked in [`docs/PROGRESS.md`](docs/PROGRESS.md).

## Stack

Java 25 (Temurin) · Gradle 9.7 (Groovy DSL, wrapper) · Spring Boot 4.1.1 · Spring Data JPA / Hibernate 7 · PostgreSQL 18 · Flyway · JUnit 5, Mockito, Testcontainers

## Prerequisites

- JDK 25 (the Gradle toolchain is pinned to 25; there is no fallback to an older Java)
- Docker with Compose (for the dev database and for the tests)
- No global Gradle needed: use `./gradlew`

## Run it

```bash
docker compose up -d --wait     # PostgreSQL 18 on 127.0.0.1:5433
./gradlew bootRun               # applies Flyway migrations, serves on :8080
```

The dev database is published on host port **5433** (not 5432) so it does not clash with a native PostgreSQL.
The credentials in `compose.yaml` and the defaults in `application.yaml` are **development-only**. Override them with
`DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for anything else; never commit real secrets.

## Test it

```bash
./gradlew test            # needs Docker running: tests start their own throwaway PostgreSQL via Testcontainers
./gradlew test --rerun    # force a fresh run (Gradle otherwise reports up-to-date results without executing)
```

| Layer | What it proves | Database |
|---|---|---|
| `ProductServiceTest` (Mockito) | Service logic and exception translation | none |
| `ProductRepositoryTest` (`@DataJpaTest`) | Real constraints: unique SKU, not null, price > 0, round trip | Testcontainers PostgreSQL |
| `ProductControllerTest` (`@WebMvcTest`) | HTTP contract: status codes, `Location`, error bodies | none (service mocked) |
| `ProductApiIntegrationTest` (`@SpringBootTest`) | End to end, including 10 concurrent creates of one SKU | Testcontainers PostgreSQL |

The tests build their schema from the same Flyway migration the app uses.

## API

Base path `/api/v1/products`.

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/api/v1/products` | `201` + `Location` header + body | `400` invalid, `409` duplicate SKU |
| `GET` | `/api/v1/products/{id}` | `200` | `400` malformed id, `404` unknown id |
| `GET` | `/api/v1/products` | `200` (plain list; no pagination yet) | |

Request fields: `sku` (required, max 64), `name` (required, max 255), `description` (optional, max 2000),
`price` (required, greater than 0, at most 10 integer digits and 2 decimals). Prices are always returned with two decimals.

```bash
# create -> 201
curl -i -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
  -d '{"sku":"KB-001","name":"Keyboard","description":"Mechanical","price":79.9}'

# fetch by the id in the Location header -> 200
curl localhost:8080/api/v1/products/<id>

# list -> 200
curl localhost:8080/api/v1/products

# same SKU again -> 409
curl -i -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
  -d '{"sku":"KB-001","name":"Other","price":10}'
```

### Error format

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`title`, `status`, `detail`, `instance`).
These are real responses captured from a running instance:

```json
// 409 Conflict
{"detail":"Product with SKU 'KB-001' already exists","instance":"/api/v1/products","status":409,"title":"Duplicate SKU"}

// 400 Bad Request: one entry per failing field
{"detail":"Request validation failed","instance":"/api/v1/products","status":400,"title":"Validation failed",
 "errors":[{"field":"price","message":"must be greater than 0"},{"field":"sku","message":"must not be blank"}]}

// 404 Not Found
{"detail":"Product not found: 00000000-0000-0000-0000-000000000000","instance":"/api/v1/products/00000000-0000-0000-0000-000000000000","status":404,"title":"Product not found"}
```

Malformed JSON and a non-UUID id also return `400` with the title `Bad Request`.
Error messages never include SQL, driver text or constraint names.

## Database

The schema is owned by Flyway: `src/main/resources/db/migration/V1__create_products.sql`. Hibernate runs with
`ddl-auto: validate`, so the app refuses to start if an entity and the schema disagree on tables, columns or column types.
Note that `validate` does **not** compare precision, scale or length; the tests against the real schema cover that.

Uniqueness of the SKU is guaranteed by the named constraint `uq_products_sku`, not by a check-then-insert in code
(which races under concurrency). The service turns a violation of that constraint, and only that one, into a `409`.
Other constraints, such as `ck_products_price_positive`, surface as a `500` because they indicate a bug rather than a client error.

To reset the dev database completely: `docker compose down -v && docker compose up -d --wait`.

## Verifying persistence across a restart (manual)

There is no automated restart test; this is the manual check.

```bash
docker compose up -d --wait && ./gradlew bootRun       # in one terminal; create a product, note its id
# stop the app (Ctrl+C), then:
docker compose restart postgres
./gradlew bootRun                                      # start again
curl localhost:8080/api/v1/products/<id>               # same id, fields and createdAt
```

## Known limitations

- No pagination, sorting, update or delete yet (Milestone 2).
- No OpenAPI documentation yet (Milestone 2); this README is the API reference for now.
- No authentication or authorization.
- CI: `.github/workflows/ci.yml` runs `./gradlew clean test` on every PR and push to `main`. It has been run locally in the same form but **not yet on GitHub** (no remote configured at the time of writing).
- A non-SKU integrity violation is rethrown by the service and handled as an unexpected error (`500`) by design; the service-level rethrow is unit-tested, but the HTTP response body for that case has not been verified.

## Documentation

- [`docs/PROGRESS.md`](docs/PROGRESS.md): stage tracker, verified results, concepts covered
- [`docs/adr/0001-modular-monolith-first.md`](docs/adr/0001-modular-monolith-first.md): architecture decision
