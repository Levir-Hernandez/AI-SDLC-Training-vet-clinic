# CLAUDE.md

## Project Context

This repository contains the Vet Clinic Service described in `TASK.md`.

Stack: Java 21 + Spring Boot 4.1 (Maven Wrapper) in `backend/`, Next.js 16 + React 19 + TypeScript + Tailwind CSS in `frontend/`, H2 in-memory database (schema from JPA entities, data resets on restart). No Docker.

---

## Where to Find Things

| What | Where |
|---|---|
| Requirements, rules, acceptance criteria, scope | `TASK.md` |
| Implementation plan and increment status | `context/PLAN.md` |
| Latest plan review, verdict and decisions log | `context/PLAN_REVIEW.md` |
| Journal and summary of steps | `context/PROGRESS.md`, `context/CHANGELOG.md` |
| Agents (`planner`, `plan-reviewer`, `implementation`) | `.claude/agents/` |
| Commands (`/implement-increment <n>`) | `.claude/commands/` |
| Plan scripts (read/check/update increments, guard hook) | `.claude/scripts/` |
| Skills (plan and journal workflows) | `.claude/skills/` |
| Path-scoped coding and testing rules | `.claude/rules/` (`java-coder`, `java-tester`, `react-coder`, `react-tester`) |
| Backend code / tests (package root `com.vetclinic`) | `backend/src/main/java/`, `backend/src/test/java/` |
| Backend configuration | `backend/src/main/resources/application.yml`, `backend/pom.xml` |
| Frontend routes / shared components / API client | `frontend/app/`, `frontend/components/`, `frontend/lib/` |
| Frontend configuration | `frontend/package.json`, `frontend/next.config.ts`, `frontend/eslint.config.mjs` |

Skip generated folders when searching: `backend/target/`, `frontend/node_modules/`, `frontend/.next/`.

---

## Run

| App | Command | URL |
|---|---|---|
| Backend | `cd backend && ./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`) | http://localhost:8080 |
| Frontend | `cd frontend && npm install` (first time), then `npm run dev` | http://localhost:3000 |

- Start the backend first, then the frontend, then open http://localhost:3000.
- The frontend reads the backend URL from `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).
- The backend allows CORS only from the origin in `app.cors.allowed-origins` (`application.yml`).
- Data lives in memory: every backend restart starts from the seeded demo data.

## Access

| Resource | URL |
|---|---|
| Health check | `GET http://localhost:8080/api/health` |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Demo accounts come from the backend seed data; see `TASK.md` and the seeder for credentials.

## Test and Verify

| Check | Command |
|---|---|
| Backend build + all tests | `cd backend && ./mvnw verify` |
| Single backend test class | `cd backend && ./mvnw test -Dtest=ClassNameTests` |
| Frontend lint | `cd frontend && npm run lint` |
| Frontend production build | `cd frontend && npm run build` |
| Frontend tests (if a test suite exists) | `cd frontend && npm test` |

An increment is done only when the checks above pass and its manual check works with both apps running.

---

## Agent Behavior

- **Understand intent first.** Read `TASK.md`, the relevant plan increment, and the existing code before proposing changes.
- **Prefer the smallest sufficient solution.** Meet the acceptance criteria without speculative features, abstractions, or dependencies.
- **Minimize blast radius.** Change only the files required by the current task or approved increment. Do not perform drive-by refactors.
- **Respect existing conventions.** Match the established package structure, naming, style, and testing patterns.
- **Make decisions traceable.** State assumptions, trade-offs, deviations, and unresolved questions explicitly, citing the relevant `TASK.md` section or criterion.
- **Do not guess through conflicts.** Stop and report when `TASK.md`, the plan, or existing code disagree.
- **Verify before claiming done.** Run the relevant build and tests and report their actual results.
- **Never commit secrets** or machine-specific absolute paths.

---

## Planning

From the main session:

1. `generate-plan` (planner) → `context/PLAN.md`
2. `review-plan` (plan-reviewer) → `context/PLAN_REVIEW.md`
3. While the verdict is not `READY`: `revise-plan [codes] [-- context]`, then `review-plan` again.

## Implementation

From the main session, one increment at a time:

1. `/implement-increment <n>` (implementation agent) checks that the increment is pending or blocked, its dependencies are completed and the plan review allows it, then implements, tests and records the outcome in `context/PROGRESS.md`.
2. The agent reads and updates the plan only through `.claude/scripts/` (`read-increment`, `check-increment-status`, `check-plan-review-status`, `update-increment-status`); a hook (`guard-plan-context.py`) blocks direct access to `PLAN.md` and `PLAN_REVIEW.md`.
3. Review the diff and test results before running the next increment. `python .claude/scripts/check-increment-status.py --list` shows every increment's status.
