# ADR-0002: Inventory does not validate or join the product catalog

- **Status:** Accepted
- **Date:** 2026-10-25

## Context

Stock records and reservations (`com.commerceflow.inventory`) refer to a product by `productId`. The catalog
(`com.commerceflow.product`) owns the products. ADR-0001 says each module owns its data and that no module reaches
into another's tables. Inventory therefore has to decide what it does about a `productId` it cannot verify.

## Decision

Inventory stores `productId` as a plain value:

- No foreign key from `stock_item` or `reservation` to `products`.
- Creating stock does **not** check that the product exists, and no code in `inventory` calls into `product`.
- Deleting a product does **not** touch its stock or reservations.

The integration test `stockCanBeCreatedForAProductTheCatalogDoesNotKnow` documents this on purpose.

## Alternatives considered

- **Foreign key to `products`.** Rejected: it couples the schemas, so extracting inventory (Milestone 5) would need
  the table split first, and it makes inventory's availability depend on the catalog's database.
- **Inventory calls `ProductService` before creating stock.** Rejected for now: it hides a cross-module dependency
  in a method call that would become a remote call, with its own failure modes, after extraction.
- **Events (`ProductCreated`, `ProductDeleted`) keep inventory in sync.** The likely end state, but it needs messaging
  and idempotent consumers, which are Milestone 6. Not worth building before there is a broker.

## Consequences

- **Good:** the modules are independent in code and in schema; inventory can be extracted, deployed and tested alone.
- **Bad:** stock can exist for a product that never existed, and it is orphaned when a product is deleted. A buyer
  could reserve units of a product the catalog no longer lists. Nothing currently reconciles this.
- **Accepted for now** because there is no ordering flow yet that would act on the inconsistency. Orders (Milestone 4)
  must not trust inventory to prove a product is valid; the catalog remains the authority on what exists.
- **Revisit when:** orders can be placed (decide who rejects an unknown product), or when events arrive in
  Milestone 6 (use `ProductDeleted` to retire stock).
