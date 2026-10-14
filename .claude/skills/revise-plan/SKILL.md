---
name: revise-plan
description: Dispatcher. Fixes findings from the PLAN_REVIEW.md of one area (backend, frontend or integration) by launching its planner subagent with revise-regular-plan.
argument-hint: "<backend|frontend|integration> [B1, M2, m1, ...]"
---

# Revise Plan

Run in the main session (`claude`), since it launches subagents.

## 1. Check and run

- First word of `$ARGUMENTS`: `backend`, `frontend` or `integration`; otherwise stop and list them.
- The rest, if any: comma-separated finding codes (`B1`, `M2`, `m1`) from the area's current `PLAN_REVIEW.md`. None → all open blockers and majors.
- `context/<area>/PLAN_REVIEW.md` must exist, otherwise stop: `Run validate-plan <area> first`.

Launch `<area>-planner`: "Run revise-regular-plan <findings>." Subagent error → change nothing and report it.

**Reply:** the `RESOLVED | NEEDS-CONTEXT | SKIPPED` lines, next step: `validate-plan <area>`.
