---
description: Implement one approved plan increment, verify its completion criteria, and record the outcome
argument-hint: "[increment: number of the plan increment to implement, e.g. 1]"
disable-model-invocation: true
context: fork
agent: implementation
---

Implement Increment `$ARGUMENTS` from `context/PLAN.md` using the `implementation` subagent.

## 1. Verify prerequisites

If `$ARGUMENTS` is empty or is not a single increment number, stop and ask for one.

Run the check scripts and confirm that:

- Increment `$ARGUMENTS` exists in `context/PLAN.md` and its status is `pending`.
- Every dependency listed by the increment is `completed`. `I0` (the existing skeleton) counts as completed.
- `context/PLAN_REVIEW.md` exists and its latest verdict is `READY` or `READY WITH MINOR CHANGES`.

If any check fails, stop. Report which prerequisite failed and the exact values found. Do not modify any files.

## 2. Implement and Validate

Implement the increment and verify each completion criterion. Then update the two files below, changing only the parts named here.

**`context/PLAN.md`**: in Increment `$ARGUMENTS` only:

- Set the status to `completed` only if every completion criterion was met, all tests pass, and there are no blockers. Otherwise set it to `blocked`.
- Mark each completion criterion as met or not met.

**`context/PROGRESS.md`**: append a section for Increment `$ARGUMENTS` (create the file if it does not exist; never rewrite earlier sections) with:

1. **Behavior delivered**: what now works that did not before.
2. **Decisions and deviations**: material choices or departures from the plan, and how each review finding targeting this increment was handled (fixed, deferred, or rejected, with a reason).
3. **Tests**: aggregate results (passed, failed, skipped).
4. **Blockers**: anything preventing the increment from being `completed`, or "None".

## 3. Reply

Reply with the resulting status, a one-line summary of each section written to `PROGRESS.md` and any blockers.