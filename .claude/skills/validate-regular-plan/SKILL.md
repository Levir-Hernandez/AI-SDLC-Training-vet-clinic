---
name: validate-regular-plan
description: Reviews the calling agent's plan (backend, frontend or integration) against TASK.md, read from the agent's perspective, and writes its PLAN_REVIEW.md with findings by severity and a verdict. Never edits the plan. Defines the review format all skills use. Called by validate-plan <area>.
---

# Validate Regular Plan

1. Read `TASK.md` as your agent definition says (section **Reading TASK.md**) and your plan files. Missing or empty plan → stop: `Run generate-plan <area> first`.
2. Read your current `PLAN_REVIEW.md` before overwriting it, to keep its history.
3. Check:
   - **Coverage:** every TASK.md code that belongs to your perspective appears in some increment's **Acceptance criteria covered** line.
   - **Perspective:** nothing from another area's perspective (see **Reading TASK.md**) and nothing out of scope per TASK.md.
   - **Ordering** (dependencies only backwards), **Size** (one slice, testable on its own, app keeps working), **Testability** (completion criteria are concrete commands or observable results) and **Assumptions** (none silently changes a requirement) and **Files** (each increment lists what it creates, modifies or deletes, consistent with Repository Findings).
   - The **validation criteria** of your agent definition, if any.
4. Write your `PLAN_REVIEW.md` next to your `PLAN.md`. Keep findings with `Source: validate-integration` untouched.
5. Run `update-progress`, then `update-changelog`.

## Review format

Each finding heading is `<code> - <title>`. The **title** is short, unique and stays the same across passes. The **code** is positional and is what `revise-regular-plan` takes: `B` blockers, `M` majors, `m` minors, numbered 1, 2, 3… in order within its section, and renumbered on every write.

```markdown
# Plan Review — <Area>

## Review Metadata
- **Date:** YYYY-MM-DD
- **Plan reviewed:** <files>
- **Review pass:** <n>
- **Verdict:** READY | READY WITH MINOR CHANGES | REVISE BEFORE IMPLEMENTATION

## Blockers
### B1 - <short title>
- **Status:** open
- **Source:** validate-regular-plan | validate-integration
- **Location:** <file › section>
- **Problem:** …
- **Evidence:** <TASK.md code, or what each area says>
- **Suggested fix:** …

## Major Findings
### M1 - <short title>
…

## Minor Findings
### m1 - <short title>
…
## Resolved in Previous Passes
- *<title> — resolved in pass <n>*
```

- **Status:** `open`, `resolved (YYYY-MM-DD)` or `needs context: <question>` (counts as open).
- Write `None.` under empty sections.
- Severity: **Blocker** = cannot be implemented correctly as written; **Major** = likely rework or failed AC; **Minor** = clarity, low risk.

| Verdict | Rule |
|---|---|
| REVISE BEFORE IMPLEMENTATION | any open blocker, or more than 2 open majors |
| READY WITH MINOR CHANGES | no blockers, 1–2 open majors |
| READY | no open blockers or majors |

**Reply:** the verdict and the open finding titles by severity.
