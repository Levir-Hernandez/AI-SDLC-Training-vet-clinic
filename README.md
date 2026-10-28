## Solution Summary

```
.claude/
├── agents/
│   ├── planner.md
│   ├── plan-reviewer.md
│   ├── implementation.md
│   ├── qa.md
│   └── refactor.md
├── commands/
│   ├── implement-increment.md
│   ├── setup-qa-env.md
│   ├── qa-env-healthcheck.md
│   ├── plan-qa.md
│   ├── run-qa.md
│   ├── implement-fix.md
│   └── verify-fix.md
├── scripts/
│   ├── read-increment.py, check-increment-status.py,
│   │   check-plan-review-status.py, update-increment-status.py   → Plan access
│   ├── read-bug.py, check-bug-status.py, update-bug-status.py    → Bug access
│   ├── qa-env.py, check-playwright.py, make-gif.py               → QA environment and evidence
│   ├── guard-plan-context.py   → Hook: blocks direct access to PLAN.md / PLAN_REVIEW.md
│   └── guard-fix-scope.py      → Hook: blocks direct access to BUGS.md / QA_REPORT.md
├── skills/
│   ├── generate-plan/, review-plan/, revise-plan/
│   └── update-progress/, update-changelog/
├── rules/
└── settings.json
```

Plan Loop: `/generate-plan` → `/review-plan [approach]` → while the verdict is not `READY`: `/revise-plan [codes] [-- context]` → `/review-plan`.
Build Loop: for each increment: `/implement-increment <n>` → review the diff and test results → next increment.
QA Loop: `/setup-qa-env` (first time) → `/qa-env-healthcheck` → `/plan-qa [focus]` (optional; without it `/run-qa` uses the default UI criteria) → `/run-qa` → bugs in `BUGS.md`.
Fix Loop: for each `Open` bug: `/implement-fix BUG-xxx` → `/verify-fix BUG-xxx` → `Verified`, or back to `Open` and fix again.

The harness is described in more detail in `HARNESS_ARCHITECTURE.md`.

## What changed

In this module I added the `qa` and `refactor` agents. I merged the tester role into the `refactor` agent: it decides how to fix the code and also adds a regression test so the bug doesn't come back. I consider that a separate tester agent would only use more tokens without a clear benefit due I already has scoped rules for test files.

I added several commands:
- `/setup-qa-env` installs the Playwright MCP if it is missing, and starts the backend and the frontend.
- `/qa-env-healthcheck` checks that these 3 things are up. If the MCP is not working, it shows the reason.

These two commands give the user confidence that tool call errors during QA are not caused by a misconfigured MCP or by the backend or frontend being down.

Like in Module 2, I added scripts that limit how the context about bugs is read: `read-bug.py`, `check-bug-status.py` and `update-bug-status.py`. The `guard-fix-scope.py` hook makes the `refactor` agent use these scripts to read bugs instead of reading the full file, and only lets it write application code, tests and the journal.

Other helper scripts start and check the environment and make it easier to capture evidence: `qa-env.py`, `check-playwright.py` and `make-gif.py`.

New context files for this module:
- `BUGS.md`: each bug found in QA, with its environment, steps to reproduce, evidence and status.
- `QA_REPORT.md`: the result of the latest QA pass, showing which acceptance criteria pass or fail, and a summary.
- `QA_SCENARIOS.md`: written when `/plan-qa` runs. It lists the scenarios to test, so the reviewer can see what was checked and that the testing was not just random clicking.

## How it was verified

Each bug in `BUGS.md` lists the exact steps to reproduce it. `/verify-fix` follows those steps and tries to produce the same evidence that was captured when the bug was present. The type of evidence depends on the bug If it shows up in a single state, like opening a page, a .png screenshot is enough. If it only appears during a sequence of actions, a .gif is captured instead. The `refactor` agent also adds a regression test so the bug doesn't come back.

## Main findings and challenges

The QA process can't be fully audited with only `BUGS.md` and `QA_REPORT.md`, because they don't show the steps the agent followed or what it checked. So I added `QA_SCENARIOS.md`: with `/plan-qa`, the scenarios the agent will test are defined before it starts looking for bugs. `/run-qa` doesn't need it, but it's useful to know what the agent chose to test. Another option I considered was to let the agent test as it prefers and then write what it did in `QA_SCENARIOS.md`. This is a bit problematic: if you add rules for the format, you limit the agent's approach, and if you don't, it may write scenarios in a format that is hard for a human to review.

Basic QA makes sure the features work, but the UX/UI is usually poor or not user friendly. The focus argument of `/plan-qa` can point the plan at specific areas, like security or UX/UI. But I think treating UX/UI issues as bugs is wrong: many of them are just different styles and are not objectively a priority.

Playwright sometimes fails to click, or tries to use buttons that don't exist. I recommend letting it fail and retry, because forcing it to read the DOM tree first instead of the accessibility tree can increase the token cost a lot.