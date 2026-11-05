# Harness Architecture

How the Claude Code harness in `.claude/` turns `TASK.md` into a reviewed plan, builds it autonomously with one command, then tests it in a browser and fixes bugs one at a time. This file is for humans; no agent, skill or rule reads it.

## 1. Structure

```
.claude/
├── agents/                 Who: role, boundaries, judgment
│   ├── orchestrator.md     Coordinates the autonomous build; reads state, delegates everything
│   ├── git-manager.md      Only commits: git add -A, git commit, git log (haiku)
│   ├── planner.md          Mode 1 plan, Mode 2 forward validation, Mode 3 escalation
│   ├── plan-reviewer.md    Reviews the plan (never edits it)
│   ├── implementation.md   Builds one increment
│   ├── code-review.md      Reviews one increment's diff; writes its CodeReview entry (gate)
│   ├── test-runner.md      Runs build, tests, lint and coverage; writes its TestRun entry (gate)
│   ├── qa.md               Tests in the browser, reports and verifies bugs
│   └── refactor.md         Fixes one bug with a regression test, or removes a repeated finding
├── commands/               Entry points typed in the main session
│   ├── run-orchestrator.md      /run-orchestrator
│   ├── escalate.md              /escalate <id> [history]
│   ├── implement-increment.md   /implement-increment <n>
│   ├── setup-qa-env.md          /setup-qa-env
│   ├── qa-env-healthcheck.md    /qa-env-healthcheck
│   ├── plan-qa.md               /plan-qa [focus]
│   ├── run-qa.md                /run-qa
│   ├── implement-fix.md         /implement-fix BUG-xxx
│   └── verify-fix.md            /verify-fix BUG-xxx
├── context/                The plan, the journal and the logs (table below)
├── hooks/                  Observability: writes context/TOOLS_USED.md
│   ├── task-start.sh, log-tool-use.sh, subagent-start.sh, subagent-stop.sh, task-finish.sh
│   └── tools_used.py       The logic behind the shell wrappers (see section 4)
├── scripts/                Deterministic file access and guards (Python)
│   ├── read-increment.py, check-increment-status.py,
│   │   check-plan-review-status.py, update-increment-status.py   Plan access
│   ├── plan-edit.py            Planner Modes 2 and 3: section-level plan changes
│   ├── read-progress.py, context-write.py   Journal, changelog and BLOCKED.md access; run counters
│   ├── preflight-orchestrator.py, coverage-report.py   /run-orchestrator checks; final 80% gate
│   ├── run-cost.py             Read-only: per-agent tokens and estimated cost from the session transcripts
│   ├── read-bug.py, check-bug-status.py, update-bug-status.py    Bug access
│   ├── qa-env.py, check-playwright.py, make-gif.py               QA environment and evidence
│   ├── guard-plan-context.py   Hook: blocks direct access to PLAN.md / PLAN_REVIEW.md / PROGRESS.md
│   ├── guard-fix-scope.py      Hook: keeps the refactor agent inside its scope
│   └── guard-orchestrator.py   Hook: one exact command per call; orchestrator read-only scripts, git-manager add/commit/log only
├── skills/                 How: steps and formats
│   ├── generate-plan/, review-plan/, revise-plan/
│   └── update-progress/, update-changelog/, generate-changelog/
├── rules/                  Path-scoped coding/testing rules (java-*, react-*)
└── settings.json           Permissions, the hooks, and the Playwright MCP server (.mcp.json)
```

| File | Role |
|---|---|
| `TASK.md` | Requirements and acceptance criteria |
| `CLAUDE.md` | Where things are, how to run and test, agent behavior |
| `context/PLAN.md`, `PLAN_REVIEW.md` | The plan with its changelog table, and its review |
| `context/PROGRESS.md`, `CHANGELOG.md` | Journal and its summary |
| `context/PROGRESS_PATTERNS.md` | The entry shapes every agent appends to the journal |
| `context/BLOCKED.md` | The global pause button; exists only when something is wrong; git-ignored, so a commit never carries it |
| `context/TOOLS_USED.md`, `.current-task` | Flight recorder of a run; git-ignored |
| `context/QA_SCENARIOS.md` | QA scenarios mapped to acceptance criteria |
| `context/BUGS.md`, `QA_REPORT.md`, `evidence/` | Created by a QA pass: bugs, results, screenshots and GIFs |

## 2. Key ideas

- **One plan, vertical increments.** Each increment is backend + frontend + tests.
- **Agents hold judgment, skills hold procedure.**
- **Author and checker are separate.** Planner vs. plan-reviewer; implementation vs. code-review and test-runner; refactor vs. qa.
- **The orchestrator only coordinates.** It has no Write or Edit tool, and a hook allows its shell only the read-only scripts. Status, changelog and git belong to `git-manager`.
- **Every agent reports its own work.** Each appends its own PROGRESS entry; `context-write.py` refuses an entry with missing fields, and `read-progress.py` derives each gate from the `CodeReview` and `TestRun` entries.
- **Counters live in files.** Retries, escalations and the validation cadence are computed from `PROGRESS.md`, so a halted run resumes where it stopped.
- **Halts are resumable.** Nothing is stashed: an unfinished attempt stays in the working tree, and the next run continues on top of it from the plan and the journal.
- **Findings keep their names.** `code-review` gets the keys used so far and the earlier attempts' findings, so a repeat is recognised and the automatic refactor can trigger.
- **Scripted access.** Agents read one increment, one bug or the last journal entries through scripts; hooks block direct access, so big files are never read or rewritten whole.
- **Scripted writes in `context/`.** Journal, plan and status changes go through scripts: they refuse malformed entries, change one section at a time, protect `completed` increments, and spare agents from loading the large files whole.
- **QA tests like a user.** The qa agent drives a real browser through Playwright MCP and writes only QA files and evidence.
- **Small fixes.** One bug per `/implement-fix`, root cause plus a regression test.
- **Statuses.** Increments: `pending` → `completed` | `blocked` | `superseded`. Bugs: `Open` → `In Progress` → `Fixed` → `Verified` (back to `Open` if reproduced).

## 3. Flow

```
Plan       generate-plan → review-plan → revise-plan   (repeat until READY)
Build      /run-orchestrator                           (or /implement-increment <n> by hand)
QA         /qa-env-healthcheck → /plan-qa → /run-qa    (bugs into BUGS.md)
Fix        /implement-fix BUG-xxx → /verify-fix BUG-xxx (one bug at a time)
```

### Inside `/run-orchestrator`

```
preflight-orchestrator.py ── BLOCKED → stop (each line says how to resolve it)
        │ OK
        ▼
┌─► pre-flight: --next ── DONE ─► test-runner (final, 80%) → planner changelog → git-manager commit → --stats + run-cost.py → Workflow Complete
│      │ NEXT <id>
│      ▼
│   --drift ── deviations or 3 passed ─► planner Mode 2 on <id> only
│      ▼
│   context once: increment, last 3 entries, --finding-keys, --findings <id>   (inlined into every prompt)
│      ▼
│   implementation  (once)   refactor Gate fix ◄── failures 1–2  (Increment / Refactor entry)
│      ▼                      ▲
│   code-review ‖ test-runner ─┤ FAIL                       (write CodeReview / TestRun entries; the gate is read from them)
│      │ PASS                  │ 3rd failure, or BLOCKED.md
│      ▼                       ▼
│   git-manager commit     /escalate ───────────── → planner Mode 3 ── split or new approach ──┐
│   (status, update-changelog, │ NEEDS HUMAN, or a 3rd escalation                                     │
│    one commit)               ▼                                                                      │
│      ▼                   Workflow Halted (code left in place, BLOCKED.md stays, next steps listed)         │
│   same Medium key twice? yes → refactor (cleanup run) → code-review ‖ test-runner → git-manager      │
└──────┴──────────────────────────────────────────────────────────────────────────────────────────────┘
```

Every prompt, tool call and finished subagent lands in `TOOLS_USED.md`, tagged with the agent that made the call; Write and Edit rows also list the rules whose paths matched.

## 4. Why the hooks are Python behind `.sh` wrappers

The assignment asks for small shell scripts, and each hook is one: a `.sh` file that forwards the payload to `tools_used.py`. A pure-bash version (jq, `date` and `awk` per call, as in the course example) was considered and measured on the Windows machine of the run, 10 runs each:

| Hook implementation | Time per call |
|---|---|
| `.sh` wrapper + `tools_used.py` | about 129 ms |
| Python alone, no wrapper | about 124 ms |
| bash + jq / `date` / `awk` | about 222 ms, with jq not even installed |
| Windows Claude Code → WSL bash | about 186 ms just to enter WSL (about 5 s when WSL is stopped) |

Git Bash on Windows starts processes slowly, so each `$(...)` costs more than the whole Python start. Pure bash would also need a new dependency (jq) and awk code to parse the transcript JSONL and match the rule globs. Inside WSL or on Linux both are fast, and the same wrappers work unchanged.

The three per-call hooks (`PostToolUse`, `SubagentStart`, `SubagentStop`) run with `"async": true`, so logging never delays a tool call. `UserPromptSubmit` and `Stop` stay synchronous, so the task block opens before its first row and closes after its last.
