# Harness Architecture

How the Claude Code harness in `.claude/` turns `TASK.md` into one reviewed implementation plan. This file is for humans; no agent, skill or rule reads it.

## 1. Structure

```
.claude/
├── agents/                 Who: role, boundaries, hierarchy of truth, judgment
│   ├── planner.md          Writes and revises context/PLAN.md
│   └── plan-reviewer.md    Reviews the plan → context/PLAN_REVIEW.md (never edits the plan)
├── skills/                 How: steps, formats, replies (plan skills run forked in their agent)
│   ├── generate-plan/      Plan format; at most 20 vertical increments          (planner)
│   ├── review-plan/        Checks, severities, verdict, decisions log           (plan-reviewer)
│   ├── revise-plan/        Fixes findings: [codes] [-- context]                 (planner)
│   ├── update-progress/    Appends an entry to PROGRESS.md
│   └── update-changelog/   Merges new entries into CHANGELOG.md [Unreleased]
└── rules/                  Path-scoped general guidelines for the implementer
    ├── java-coder.md       backend/src/main, pom.xml
    ├── java-tester.md      backend/src/test
    ├── react-coder.md      frontend sources
    └── react-tester.md     frontend tests
```

| File | Role |
|---|---|
| `TASK.md` | Requirements: application, flows, business rules, technology constraints, acceptance criteria, out of scope |
| `CLAUDE.md` | Where things are, how to run and test, general agent behavior |
| `context/PLAN.md` | The single plan: increments with status, API and views summaries, assumptions |
| `context/PLAN_REVIEW.md` | Findings by severity, verdict, resolved findings, decisions log |
| `context/PROGRESS.md` | Append-only journal, one entry per action (Added / Changed / Fixed / Removed) |
| `context/CHANGELOG.md` | Keep a Changelog summary built from the journal |

## 2. Key ideas

- **One plan, one source of truth.** Each increment is a vertical slice (backend + frontend + tests), so nothing has to be synchronized between plans.
- **Agents hold judgment, skills hold procedure.** Agents define role, boundaries, hierarchy of truth and judgment; skills define steps, formats and replies.
- **Separate author and reviewer.** The reviewer never edits the plan (no `Edit` tool). The planner only marks findings `fixed` and appends user context to the decisions log.
- **Strict ordering.** Increments depend only on I0 (the skeleton) or earlier increments; forward dependencies are reordered or re-split, never left as placeholders.
- **Consistency focus in review.** Increment order, backend API definitions and frontend usage of that API are checked against each other and against the `TASK.md` state diagram.
- **User decisions are recorded.** Context passed to `revise-plan` after `--` is logged verbatim in the decisions log and carried forward on every review pass.
- **Preservation.** Regeneration never changes `in-progress` or `done` increments.
- **Verdicts.** `READY` (no open blockers or majors) · `READY WITH MINOR CHANGES` (1–2 open majors) · `REVISE BEFORE IMPLEMENTATION` (any blocker or more than 2 majors). `needs context` counts as open.
- **Simple runtime.** H2 in-memory, JWT auth (HttpOnly cookie on the frontend), no Docker. Each increment is verified with `./mvnw verify`, `npm run lint`, `npm run build` and a manual check with both apps running.

## 3. Flow

```
1. generate-plan              (planner)        → PLAN.md
2. review-plan                (plan-reviewer)  → PLAN_REVIEW.md + verdict
3. revise-plan [codes] [-- context]  (planner) → PLAN.md, findings marked fixed, context logged
   repeat 2–3 until READY
Each step ends with update-progress → update-changelog.
```

Findings use positional codes (`B1`, `M1`, `m1`) renumbered every pass; titles stay stable so findings can be tracked across passes.
