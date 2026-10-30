# Harness Architecture

How the Claude Code harness in `.claude/` turns `TASK.md` into a reviewed plan, builds it increment by increment, then tests it in a browser and fixes bugs one at a time. This file is for humans; no agent, skill or rule reads it.

## 1. Structure

```
.claude/
├── agents/                 Who: role, boundaries, judgment
│   ├── planner.md          Writes and revises the plan
│   ├── plan-reviewer.md    Reviews the plan (never edits it)
│   ├── implementation.md   Builds one increment
│   ├── qa.md               Tests in the browser, reports and verifies bugs
│   └── refactor.md         Fixes one bug and implement regression tests
├── commands/               Entry points typed in the main session
│   ├── implement-increment.md   /implement-increment <n>
│   ├── setup-qa-env.md          /setup-qa-env
│   ├── qa-env-healthcheck.md    /qa-env-healthcheck
│   ├── plan-qa.md               /plan-qa [focus]
│   ├── run-qa.md                /run-qa
│   ├── implement-fix.md         /implement-fix BUG-xxx
│   └── verify-fix.md            /verify-fix BUG-xxx
├── scripts/                Deterministic file access and guards (Python)
│   ├── read-increment.py, check-increment-status.py,
│   │   check-plan-review-status.py, update-increment-status.py   Plan access
│   ├── read-bug.py, check-bug-status.py, update-bug-status.py    Bug access
│   ├── qa-env.py, check-playwright.py, make-gif.py               QA environment and evidence
│   ├── guard-plan-context.py   Hook: blocks direct access to PLAN.md / PLAN_REVIEW.md
│   └── guard-fix-scope.py      Hook: keeps the refactor agent inside its scope
├── skills/                 How: steps and formats
│   ├── generate-plan/, review-plan/, revise-plan/
│   └── update-progress/, update-changelog/
├── rules/                  Path-scoped coding/testing rules (java-*, react-*)
└── settings.json           Permissions + enables the Playwright MCP server (.mcp.json)
```

| File | Role |
|---|---|
| `TASK.md` | Requirements and acceptance criteria |
| `CLAUDE.md` | Where things are, how to run and test, agent behavior |
| `context/PLAN.md`, `PLAN_REVIEW.md` | The plan and its review |
| `context/QA_SCENARIOS.md` | QA scenarios mapped to acceptance criteria |
| `context/BUGS.md` | Bugs with steps, evidence and status |
| `context/QA_REPORT.md` | Latest QA pass results |
| `context/evidence/` | Screenshots and GIFs |
| `context/PROGRESS.md`, `CHANGELOG.md` | Journal and its summary |

## 2. Key ideas

- **One plan, vertical increments.** Each increment is backend + frontend + tests.
- **Agents hold judgment, skills hold procedure.**
- **Author and checker are separate.** Planner vs. plan-reviewer; refactor vs. qa.
- **Scripted access.** Agents read one increment or one bug through scripts; hooks block direct access, so big files are never read or rewritten whole.
- **QA tests like a user.** The qa agent drives a real browser through Playwright MCP and writes only QA files and evidence.
- **Small fixes.** One bug per `/implement-fix`, root cause plus a regression test.
- **Statuses.** Increments: `pending` → `completed` | `blocked`. Bugs: `Open` → `In Progress` → `Fixed` → `Verified` (back to `Open` if reproduced).

## 3. Flow

```
Plan       generate-plan → review-plan → revise-plan   (repeat until READY)
Build      /implement-increment <n>                    (one increment at a time)
QA         /qa-env-healthcheck → /plan-qa → /run-qa    (bugs into BUGS.md)
Fix        /implement-fix BUG-xxx → /verify-fix BUG-xxx (one bug at a time)
```

Planning, build and fix steps end with `update-progress` → `update-changelog`. Review each diff and its evidence before the next step.
