---
name: validate-plan
description: Dispatcher. Validates the plan of one area (backend, frontend or integration) by launching its planner subagent with validate-regular-plan. Only that area's PLAN_REVIEW.md changes.
argument-hint: "<backend|frontend|integration>"
---

# Validate Plan

Run in the main session (`claude`), since it launches subagents.

## 1. Check and run

`$ARGUMENTS` must be `backend`, `frontend` or `integration`; otherwise stop and list them. `context/<area>/PLAN.md` must exist, otherwise stop: `Run generate-plan <area> first`.

| Area | Runs |
|---|---|
| `backend` | launch `backend-planner`: "Run validate-regular-plan." |
| `frontend` | launch `frontend-planner`: "Run validate-regular-plan." |
| `integration` | launch `integration-planner`: "Run validate-regular-plan." |

Subagent error → change nothing and report it.

## 2. Read the verdict

Read the **Verdict** of `context/<area>/PLAN_REVIEW.md`.

**Reply:** the verdict and open finding titles by severity. Next step: `revise-plan <area>` if findings are open; otherwise the next area (`backend` → `frontend` → `integration`), and after `integration`, `validate-integration`.
