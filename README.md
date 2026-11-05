## Solution Summary

`/run-orchestrator` wraps the manual flow from the earlier modules into one command that builds every pending increment. Every earlier agent and rule stays; the new piece is the coordination layer. The structure and flow are in `HARNESS_ARCHITECTURE.md`.

Build Loop: for each pending increment, pre-flight (with a drift check) → `implementation` → `code-review` + `test-runner` in parallel → `git-manager` (status, `update-changelog`, one commit). `run-cost.py` reports the cost at the end. Three failed attempts trigger planner Mode 3 through `/escalate`. Deviations, or every 3 increments, trigger planner Mode 2. The run ends with `generate-changelog` and `Workflow Complete`.

## What changed

| Task item | Where |
|---|---|
| 1. Orchestrator agent | `.claude/agents/orchestrator.md`, with `guard-orchestrator.py` and `check-increment-status.py --next` |
| Agents the orchestrator needs | `.claude/agents/code-review.md`, `.claude/agents/test-runner.md`, `.claude/agents/git-manager.md` |
| 2. Planner Modes 2 and 3, plan changelog table | `.claude/agents/planner.md`, `plan-edit.py`, `## Plan Changelog` in `PLAN.md`, `/escalate` |
| 3. Observability hooks (the four required, plus `SubagentStart` for the `(start)` rows the slides show) | `.claude/hooks/`, `hooks` in `.claude/settings.json`, `.gitignore` |
| 4. `/run-orchestrator` | `.claude/commands/run-orchestrator.md`, `preflight-orchestrator.py` |
| Shared language between agents | `context/PROGRESS_PATTERNS.md`, `BLOCKED.md`, `read-progress.py`, `context-write.py` |
| Finalise | `.claude/skills/generate-changelog/`, `coverage-report.py` |

Clean-up for the run: `context/` moved to `context/`, as the assignment names it. Every increment in `PLAN.md` was reset to `pending`, the journal was emptied, and the old bugs, QA report and evidence were removed.

Plan changes: I1 now adds JaCoCo, since nothing measured the required 80% coverage (line and branch, enforced at the final gate). A `Lessons From The Previous Build` section carries the three Module 3 bugs, because the plan alone would rebuild them. These two edits came after the plan review's `READY` verdict and have not been reviewed.

Optional extensions applied:
- **Cost control:** `code-review` and `test-runner` run on haiku; orchestrator, planner, implementation and refactor run on sonnet. Prompt caching is on by default in Claude Code.
- **Context trimming:** the orchestrator passes the increment and the last 3 PROGRESS entries inline, and `guard-plan-context.py` now also blocks direct reads of `PROGRESS.md`.
- **Automatic refactor:** the same Medium finding on 2 consecutive increments triggers a `refactor` cleanup run on a green baseline.
- **Plan quality signal:** `read-progress.py --stats` adds a warning to `Workflow Complete` when more than 40% of increments needed retries.

## v2 changes

v2 is a copy of the first harness, changed after a live test of I1–I3 (`CONCLUSIONS.md`):

| Problem in the test run | v2 change |
|---|---|
| A halt left uncommitted work, and the pre-flight then refused the dirty tree | Halts stash only `backend frontend` under a named message; `BLOCKED.md` is git-ignored; the pre-flight accepts journal and plan changes, names left-over stashes and says how to resolve each block |
| (found while fixing it) `git stash push -u` also stashed the uncommitted Gate entries, and with them the retry and escalation counters | Same pathspec stash |
| Medium findings of a failed attempt disappeared on the retry | `read-progress.py --findings <id>` goes to the retry's review, which re-reports what is still there |
| The same problem got a new key per increment, so the refactor never triggered | `read-progress.py --finding-keys` goes to `code-review` as `Known finding keys`; keys name the kind of problem |
| `test-runner` checked 80% coverage during an increment | `mode: increment` runs the coverage script without `--min`; the I1 rerun was really caused by the build below |
| 31 failed tool calls, mostly `cd ... &&`, pipes, loops, `${...}` and heredoc file writes | Plain-command rules in each agent's own file; no allow rule was missing |
| `npm run build` stopped with Next.js's `SecurityFatalError` notice | `test-runner` retries only that exact error once and ignores its upgrade instructions |
| Date-only journal headings | `context-write.py` gives every heading the current time |
| `jacoco-maven-plugin` without a version | I1 pins `0.8.16` (latest on Maven Central) |
| One combined token number per agent, no cost | `(done)` rows split input, cache write, cache read and output; new `run-cost.py` |
| `update-changelog` reread the whole journal per increment | `read-progress.py --since-changelog` with a marker line in `CHANGELOG.md` |
| The orchestrator wrote the `Gate` entries for `code-review` and `test-runner`, and `implementation` skipped fields (I1, I2, I3 attempt 2 have no files, interfaces, decisions or tests) | Every agent writes its own entry (`CodeReview`, `TestRun`); `context-write.py` refuses an entry with missing fields; `read-progress.py` derives the gate from the two entries |
| The orchestrator ran git, the status and the changelog itself | New `git-manager` agent (haiku) does them; the orchestrator's guard allows only read-only scripts, one exact command per call |
| Mode 2 ran after the commit, on the next 3 increments | The pre-flight runs `read-progress.py --drift`; on deviations or every 3 increments, planner Mode 2 checks only the increment about to start |

The orchestrator was also restructured after the course's reference orchestrator: Core Identity, Shared Context Files, Workflow Selection, Circuit Breaker, On-Demand Agents, a STOP/OK Self-Check, Subagent Invocation (paste the context, not the command to fetch it), Quality Gates, Communication Format and a wider Error Recovery table. The logging hooks run async; `HARNESS_ARCHITECTURE.md` section 4 explains why they stay Python behind `.sh` wrappers.

## How to run it

The scaffolding and the harness must be committed first, on a feature branch. To start from the original repository, with the code back at the skeleton (commit `314286f`, Increment 0):

```bash
git switch -c module-4-orchestrator
git rm -rq backend frontend context .claude
git checkout 314286f -- backend frontend
# copy this harness into the repository root, then:
git add -A && git commit -m "Module 4 harness on a clean skeleton"
claude --permission-mode acceptEdits
# accept the trust dialog if asked: an untrusted folder ignores the allow rules. Inside the session:
/run-orchestrator
```

Watch `context/TOOLS_USED.md` while it runs.

Lessons from the test run:
- Do not set `MSYS_NO_PATHCONV=1` for the session; the Maven wrapper then fails with `Could not find or load main class ...Launcher`.
- Do not pass `Write` or `Edit` in `--allowedTools`; `--permission-mode acceptEdits` already covers the working directory.
- Check that the skeleton matches the plan (H2, no Docker) before starting.
- Budget about $1.15 per increment at API rates (`run-cost.py`).

## Results of the run

> Fill in after running. Numbers come from `python .claude/scripts/read-progress.py --stats`, `/cost` and `TOOLS_USED.md`.

| Measure | Result |
|---|---|
| Increments completed autonomously (first try) | |
| Increments that required retries | |
| Mode 3 escalations | |
| Forward validations (Mode 2) | |
| Refactor cleanup runs | |
| Rough cost of the run, against the Module 2 manual flow | |
| Optimisation that helped most | |

Verification after `Workflow Complete`:
- [ ] Full test suite passes with ≥ 80% line and branch coverage (`./mvnw verify`, `coverage-report.py --min 80`).
- [ ] `./mvnw spring-boot:run` and `npm run dev` start the apps on a clean checkout.
- [ ] Behaviour matches `TASK.md`, including the plan's Manual checks, which the gates do not run.
- [ ] The `CHANGELOG.md` entry accurately summarises what was built.

## Main findings and challenges

Design findings, before the run:

- **`context/` lives at the repository root.** It used to sit in `.claude/`, where Write and Edit always prompt; it was moved beside `backend/` and `frontend/`. Routine writes still go through scripts, for validation, section-level edits and token cost rather than to avoid a prompt. Planning and QA, which run with a person present, still write some context files directly.
- **Retries are ambiguous in the material.** The assignment says "maximum 3 retries", but the slides show one attempt plus two retries and the assignment also says "fails three times". I implemented 3 failed attempts, then Mode 3.
- **The breaker needs its own breaker.** A split increment can fail again, so a second escalation of the same increment family is allowed and the third attempt halts the run.
- **Manual checks are not automated.** The plan's Manual section needs both apps and a browser, so the gates skip it. That remains a human or `/run-qa` step.

- **Trust is a hard prerequisite.** In an untrusted folder Claude Code ignores the allow rules and the agents' frontmatter hooks, so the guards silently do not run. Accept the trust dialog once before the run.
- **Long paths lose token counts.** Windows cannot open a subagent transcript past 260 characters, and the row then shows `—`.

Harness smoke test, on a throwaway two-increment project with a stub build, and the live test of I1–I3 on the vet clinic (`CONCLUSIONS.md`):

| Behavior | Result |
|---|---|
| `/run-orchestrator` forks the orchestrator, which spawns and waits for subagents | Confirmed live |
| `code-review` + `test-runner` issued in one message, overlapping in time | Confirmed live |
| Gate recorded, status updated, one commit per increment | Confirmed live (I1–I3) |
| Retry with feedback after a failed gate | Confirmed live (I3) |
| Planner Mode 2, with its Plan Changelog row | Confirmed live (after I2) |
| Halt on `BLOCKED.md` | Confirmed live |
| `BLOCKED.md`, `/escalate`, planner Mode 3, `NEEDS HUMAN`, `Workflow Halted` | Confirmed in the smoke test (I2) |
| The hooks, rule tagging and per-agent token rows in `TOOLS_USED.md`, `--stats` | Confirmed live |
| `plan-edit.py` split and rewire, retry and escalation counters, 40% warning, guards | Confirmed by calling the scripts directly |
| `Workflow Complete`, `generate-changelog`, the final 80% gate, Mode 3 on real code, a refactor cleanup run, resuming after a halt | Not exercised |
| v2 changes (resume, findings carry-over, stable keys, build retry, async hooks, `run-cost.py`, changelog marker) | Scripts and hooks tested with sample input; not yet in a live run |
| Agent frontmatter guards and `settings.json` allow rules inside a live session | Not exercised (both runs used an untrusted folder) |

Findings from it: haiku invented an absolute path and reported a blocked review as `FAIL`, so the gate agents now must use relative paths and answer `ERROR` when they cannot run. The reviewer also rejected a marker comment added only to satisfy the build, and the planner refused to re-scope around it, which is the right outcome.

Run findings:

> Fill in after running: where the autonomous flow surprised you or needed intervention.
