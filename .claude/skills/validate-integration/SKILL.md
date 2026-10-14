---
name: validate-integration
description: Moves the validated Suggested Integration Adjustments of context/integration/PLAN.md into the backend and frontend PLAN_REVIEW.md as findings (creating the reviews if missing), then removes them from the integration plan, leaving only its increments. Run in the main session after validate-plan integration is READY.
---

# Validate Integration

Run in the main session (`claude`), after `validate-plan integration`.

## 1. Read

- `context/integration/PLAN.md`, section `## 7. Suggested Integration Adjustments`
- `context/integration/PLAN_REVIEW.md`: any open blocker or major → stop: `Run revise-plan integration first`. Open minors don't block: move the adjustments as they are.
- `context/backend/PLAN_REVIEW.md` and `context/frontend/PLAN_REVIEW.md`, if they exist

Section missing or `None.` → nothing to move; reply so and stop.

## 2. Add findings

For each adjustment, in the `PLAN_REVIEW.md` of its **Area**, using the `validate-regular-plan` format:

- Missing file → create it (`Review pass: 1`, `None.` in empty sections).
- An open finding with the same title already there → skip it.
- Otherwise add it under the section of its **Severity** (Blockers, Major Findings, Minor Findings):
  - **Status:** open
  - **Source:** validate-integration
  - **Location:** `context/<area>/PLAN.md` › its **Increment**
  - **Problem**, **Evidence**, **Suggested fix:** from the adjustment
- Renumber the codes (`B1`, `M1`, `m1`…) and recompute the **Verdict**. Touch no other finding.

## 3. Clean the integration plan

Remove `## 7. Suggested Integration Adjustments` from `context/integration/PLAN.md`. Change nothing else.

## 4. Log

Run `update-progress`, then `update-changelog`, in `context/integration/` (action `Integration check`).

**Reply:** per area, the titles added and skipped and the resulting verdict. Next step: `revise-plan <area>` for each area with new findings.
