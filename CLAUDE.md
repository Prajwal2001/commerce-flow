# CommerceFlow: working agreement

## Goal of this project
This is a **learning project**. The owner (Prajwal) is building a Java e-commerce backend milestone by
milestone, as a modular monolith that can later be split into services (see `docs/adr/0001`). The point
is for the owner to understand each concept, not to ship fast. Optimise for learning over speed.

## Roles (strict)
- **Claude writes the tests** (TDD: tests first, red before green) and reviews.
- **The owner writes the production code** in `src/main`. Claude does **not** edit `src/main` unless the
  owner explicitly asks for it in that message. Not even as a "quick experiment": if an experiment needs
  source changes, describe it and let the owner make them.
- Claude may edit: `src/test`, `docs/`, and test-support code. Everything under `src/main` is the owner's.
- Allowed in `src/main` without asking: nothing. When a test needs a stub to compile, say so and let the
  owner write it.

## How to teach
- Explain the *why* and the trade-offs; don't just hand over the answer. Let the owner reach conclusions
  (including ADR text and findings) in their own words; Claude fact-checks them.
- Don't pre-empt a design decision the owner is meant to work out. Give hints on request.
- Report outcomes faithfully: say what failed, what was not verified, and what a passing test does not prove.
- Prefer measured evidence over claims. Mutation-check tests (break the code, confirm the right tests fail).

## Workflow per slice
1. Claude writes failing tests (confirm they fail for the right reason).
2. Owner implements; Claude reviews and runs the suite.
3. Mutation checks, then review findings go into `docs/PROGRESS.md`.
4. Concepts learned go into `docs/LEARNING.md`; decisions into `docs/adr/`.
5. Open a PR per feature branch. Commit/push only when asked.

## Where the state lives (read these first in a new session)
- `docs/PROGRESS.md`: what is done, verified results, and **"Next task"** at the bottom.
- `docs/LEARNING.md`: the owner's learning notes.
- `docs/adr/`: architecture decisions.

## Conventions seen so far
- Stack: Java 25, Spring Boot 4.1, PostgreSQL + Flyway, Testcontainers, springdoc, Gradle (`./gradlew test`).
- Never add a catch-all `void` `@ExceptionHandler` (it returns 200); error bodies are `ProblemDetail`.
- Concurrency claims must be backed by concurrent tests (`support/Concurrently`), run more than once.
- Don't leave the Spring Boot app running in the background after checking something.
