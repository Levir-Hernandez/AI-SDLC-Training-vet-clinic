# Harness Architecture

How the Claude Code harness in `.claude/` turns `TASK.md` into three implementation plans: backend, frontend and integration.

## 1. Structure

```
.claude/
├── agents/                       Planner subagents; each reads TASK.md from its own perspective
│   ├── backend-planner.md        Data, operations, rules → context/backend/PLAN.md (+ API Contract)
│   ├── frontend-planner.md       What the user sees and does → context/frontend/PLAN.md
│   └── integration-planner.md    Run, configuration, contract → context/integration/PLAN.md
├── skills/
│   ├── generate-plan/            Dispatcher <area>: launches the area planner to write its plan
│   ├── validate-plan/            Dispatcher <area>: launches the area planner to review its plan
│   ├── revise-plan/              Dispatcher <area> [codes]: launches the area planner to fix findings
│   ├── generate-regular-plan/    Backend/frontend plan; defines the plan format
│   ├── generate-integration-plan/  Integration increments + Suggested Integration Adjustments
│   ├── validate-regular-plan/    Writes PLAN_REVIEW.md; defines the review format and verdicts
│   ├── revise-regular-plan/      Fixes findings in the agent's own plan
│   ├── validate-integration/     Moves the adjustments into the backend/frontend reviews
│   ├── update-progress/          Appends to PROGRESS.md
│   └── update-changelog/         Summarizes PROGRESS.md into CHANGELOG.md
└── rules/                        Path-scoped coding rules, loaded when matching files are touched
    ├── java-coder.md             backend/src/main, pom.xml
    ├── java-tester.md            backend/src/test
    ├── react-coder.md            frontend/app, components, lib, config
    ├── react-tester.md           frontend tests
    └── docker-expert.md          compose.yaml, Dockerfiles
```

Output per area in `context/<backend|frontend|integration>/`: `PLAN.md`, `PLAN_REVIEW.md`, `PROGRESS.md`, `CHANGELOG.md`.

## 2. Key ideas

- **No task splitting.** There is no lead planner; every planner reads the whole `TASK.md` and keeps what its expertise needs (e.g. "only the assigned vet" is an authorization check for the backend and a hidden button for the frontend).
- **Dispatchers vs. regular skills.** `generate-plan`, `validate-plan` and `revise-plan` run in the main session (`claude`) and launch the subagent; the `*-regular-plan` skills do the work inside it.
- **Each area owns its files.** A planner never edits another area's plan or review. The integration planner proposes changes to backend/frontend as **Suggested Integration Adjustments**; `validate-integration` turns them into findings in those reviews.
- **Verdicts.** `READY` (no open blockers or majors) · `READY WITH MINOR CHANGES` (1–2 majors) · `REVISE BEFORE IMPLEMENTATION` (any blocker or >2 majors). Minors never block.

## 3. Flow

```
 1. generate-plan backend          generate-plan frontend
 2. validate-plan backend          validate-plan frontend
 3. revise-plan backend            revise-plan frontend      → repeat 2–3 until no blockers/majors

 4. generate-plan integration      increments + Suggested Integration Adjustments
 5. validate-plan integration      reviews only the integration plan (may move an adjustment into an increment)
 6. revise-plan integration        → repeat 5–6 until no blockers/majors

 7. validate-integration           adjustments → findings in backend/frontend PLAN_REVIEW.md;
                                   removed from the integration plan
 8. revise-plan backend|frontend   → validate-plan backend|frontend until no blockers/majors

 ✓ Backend, frontend and integration plans are READY and consistent with each other.
```

| Step | Writes |
|---|---|
| generate-plan `<area>` | `context/<area>/PLAN.md` |
| validate-plan `<area>` | `context/<area>/PLAN_REVIEW.md` |
| revise-plan `<area>` | `context/<area>/PLAN.md`, finding statuses in its review |
| validate-integration | backend/frontend `PLAN_REVIEW.md`, integration `PLAN.md` (removes section 7) |

Every step also appends to the area's `PROGRESS.md` and `CHANGELOG.md`.
