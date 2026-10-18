# ADR-0001: Modular monolith first, extract services later

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

CommerceFlow will grow into several business capabilities (catalog, orders, payments, inventory, and so on). Starting as
separate microservices would add network calls, independent deployments, distributed data and observability needs before
the domain boundaries are understood. This is a one-developer project that must stay runnable on a laptop with
`docker compose up` and `./gradlew bootRun`.

## Decision

Build a single deployable Spring Boot application, organised **by feature**: each capability lives in its own package
(today `com.commerceflow.product`) and owns its entity, repository, service, controller, DTOs and exceptions.
Extract a capability into its own service later (planned for Milestone 5), only when there is a concrete reason.

Rules that keep extraction cheap:

1. **Own your data.** A module's tables are used only by that module. Other modules go through its service or API, never
   its repository or tables, and no foreign keys cross module boundaries.
2. **Keep the surface small.** Entities, repositories and services stay inside the module; the controller, DTOs and
   exceptions are the public face. Entities never appear in controller signatures.
3. **No business knowledge in `common`.** Shared code (for example the generic exception handler) must not depend on
   any business package. Each module maps its own exceptions to HTTP responses.
4. **The database enforces invariants.** Constraints live in Flyway migrations with stable names, so the guarantee
   survives any caller, including a future second service.

Kafka, Kubernetes, Redis and distributed transactions are deferred until a requirement needs them.

## Alternatives considered

- **Microservices from day one.** Rejected: operational cost (service discovery, per-service deployment, distributed
  tracing, eventual consistency) before the boundaries are known, with no scaling or team-size pressure to justify it.
- **A layered monolith (`controller/`, `service/`, `repository/` for everything).** Rejected: it hides boundaries, so
  modules grow tangled and extraction later means untangling everything at once.

## Consequences

- **Good:** one build, one deploy, local transactions, simple debugging, fast tests; module boundaries are visible in
  the package structure and can be checked.
- **Bad:** the boundaries are enforced by convention and review, not by the network; a shortcut across modules compiles
  fine. A shared database instance means a noisy module can affect others until a service is split out.
- **Revisit when:** a module needs independent scaling or release cadence, or its team or failure domain should be
  separated. The first extraction will be the real test of the rules above and should update this ADR.
