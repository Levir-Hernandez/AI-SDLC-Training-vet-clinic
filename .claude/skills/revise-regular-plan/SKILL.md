---
name: revise-regular-plan
description: Fixes findings from the calling agent's PLAN_REVIEW.md (backend, frontend or integration). Takes comma-separated finding codes (B1, M2, m1); with none, fixes all open blockers and majors. Called by revise-plan <area>.
argument-hint: "[B1, M2, m1, ...]"
---

# Revise Regular Plan

1. Read your `PLAN_REVIEW.md` and plan files.
2. Pick findings: the codes (`B1`, `M2`, `m1`) in `$ARGUMENTS`, as they are in the current `PLAN_REVIEW.md`; with none, all open **blockers** and **majors**. Skip and report unknown or resolved ones.
   Codes are case-sensitive: `M1` is a major, `m1` a minor.
3. For each, make the smallest consistent edits in your plan files (increment, files, tests, criteria). Never rewrite the whole plan.
4. If a fix needs a human decision or a change in another area, don't guess and edit nothing for it.
5. In `PLAN_REVIEW.md` change only each finding's **Status** (keep codes as they are until the next validation): `resolved (YYYY-MM-DD)` or `needs context: <question>`.
6. Run `update-progress`, then `update-changelog`.

**Reply:** one line per finding, then `Next: validate-plan <area>`.

```
RESOLVED <code> - <title>: <what changed>
NEEDS-CONTEXT <code> - <title>: <question>
SKIPPED <code>: <reason>
```
