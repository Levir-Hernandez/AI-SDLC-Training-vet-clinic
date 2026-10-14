---
name: generate-plan
description: Dispatcher. Generates the plan of one area (backend, frontend or integration) by launching its planner subagent with the matching skill.
argument-hint: "<backend|frontend|integration>"
---

# Generate Plan

Run in the main session (`claude`), since it launches subagents.

## 1. Check and run

`$ARGUMENTS` must be `backend`, `frontend` or `integration`; otherwise stop and list them.

| Area | Requires | Runs |
|---|---|---|
| `backend` | `TASK.md` | launch `backend-planner`: "Run generate-regular-plan." |
| `frontend` | `TASK.md` and `## API Contract` in `context/backend/PLAN.md` | launch `frontend-planner`: "Run generate-regular-plan." |
| `integration` | `TASK.md`, `context/backend/PLAN.md`, `context/frontend/PLAN.md` | launch `integration-planner`: "Run generate-integration-plan." |

- Missing input → stop and say what to run first, e.g. `Run generate-plan backend first`.
- Subagent error → change nothing and report it.

The subagent logs its own `PROGRESS.md` and `CHANGELOG.md`; add nothing.

**Reply:** summary of the subagent output, next step: `validate-plan <area>`.
