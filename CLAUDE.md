# CLAUDE.md

## Project Context

This repository contains the Vet Clinic Service described in `TASK.md`.

- `TASK.md` defines the required behavior, acceptance criteria, and scope (codes UF, BR, UI, TC, AC).
- `context/<backend|frontend|integration>/PLAN.md` hold the increments to implement, each with its status.
- Each folder in `context/` (`backend`, `frontend`, `integration`) also holds `PLAN_REVIEW.md` (latest review and verdict), `PROGRESS.md` (journal) and `CHANGELOG.md` (summary).
- `.claude/agents/` holds the backend, frontend and integration planners, each reading `TASK.md` directly from its own perspective; `.claude/skills/` their skills.
- `.claude/rules/` holds path-scoped coding rules: `java-coder`, `java-tester`, `react-coder`, `react-tester`, `docker-expert`.

Stack: Java 21 + Spring Boot 4.1 (Maven Wrapper) in `backend/`, Next.js 16 + React 19 + TypeScript in `frontend/`, PostgreSQL 17 from the root `compose.yaml`, Flyway migrations, Testcontainers for integration tests.

---

## Repository Layout

```
backend/    Spring Boot application (package root: com.vetclinic)
frontend/   Next.js App Router application
compose.yaml  Local PostgreSQL used by `spring-boot:run` (Spring Boot Docker Compose support)
context/    Planning and progress artifacts
.claude/    Planner agents, skills and scoped rules
```

---

## Commands

| Purpose                     | Command                                         |
|-----------------------------|-------------------------------------------------|
| Start database only         | `docker compose up -d`                          |
| Full stack in containers   | `docker compose --profile app up --build`       |
| Run backend                 | `cd backend && ./mvnw spring-boot:run`          |
| Build + test backend        | `cd backend && ./mvnw verify`                   |
| Run frontend                | `cd frontend && npm run dev`                    |
| Build / lint frontend       | `cd frontend && npm run build` / `npm run lint` |
| Health check                | `GET http://localhost:8080/api/health`          |

Docker must be running for both `spring-boot:run` (compose) and `./mvnw verify` (Testcontainers).

---

## Agent Behavior

- **Understand intent first.** Read `TASK.md`, the relevant plan increment, and the existing code before proposing changes.
- **Prefer the smallest sufficient solution.** Meet the acceptance criteria without speculative features, abstractions, or dependencies.
- **Minimize blast radius.** Change only the files required by the current task or approved increment. Do not perform drive-by refactors.
- **Respect existing conventions.** Match the established package structure, naming, style, and testing patterns.
- **Make decisions traceable.** State assumptions, trade-offs, deviations, and unresolved questions explicitly, and reference AC/BR numbers from `TASK.md`.
- **Do not guess through conflicts.** Stop and report when `TASK.md`, the plans, or existing code disagree.
- **Verify before claiming done.** Run the relevant build and tests and report their actual results.
- **Never commit secrets** or machine-specific absolute paths.

---

## Planning

See `HARNESS_ARCHITECTURE.md` for the `.claude/` structure and the full flow.

Each planner reads `TASK.md` itself from its own perspective. From the main session (`claude`), run per area (`backend`, `frontend`, `integration`, in that order, since each builds on the previous plans):

1. `generate-plan <area>`
2. `validate-plan <area>` (only that area's `PLAN_REVIEW.md` changes)
3. `revise-plan <area> [codes]` while findings are open, then `validate-plan <area>` again

The integration plan also holds **Suggested Integration Adjustments**: small changes to `pending` backend or frontend increments. Once `validate-plan integration` is READY, run `validate-integration`: it moves them as findings into the backend and frontend reviews and removes them from the integration plan. Then `revise-plan backend|frontend` and validate those areas again.

Each dispatcher launches `<area>-planner` with its `*-regular-plan` skill (`generate-integration-plan` for integration).
