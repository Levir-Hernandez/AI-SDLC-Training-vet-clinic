## Solution Summary

```
.claude/
├── agents/
│   ├── orchestrator.md
│   ├── git-manager.md
│   ├── planner.md
│   ├── plan-reviewer.md
│   ├── implementation.md
│   ├── code-review.md
│   ├── test-runner.md
│   ├── qa.md
│   └── refactor.md
├── commands/
│   ├── run-orchestrator.md
│   ├── escalate.md
│   ├── implement-increment.md
│   ├── setup-qa-env.md
│   ├── qa-env-healthcheck.md
│   ├── plan-qa.md
│   ├── run-qa.md
│   ├── implement-fix.md
│   └── verify-fix.md
├── context/
├── hooks/
│   ├── task-start.sh, log-tool-use.sh, subagent-start.sh, subagent-stop.sh, task-finish.sh
│   └── tools_used.py
├── scripts/
│   ├── read-increment.py, check-increment-status.py, check-plan-review-status.py, update-increment-status.py
│   ├── plan-edit.py
│   ├── read-progress.py, context-write.py
│   ├── preflight-orchestrator.py, coverage-report.py
│   ├── run-cost.py
│   ├── read-bug.py, check-bug-status.py, update-bug-status.py
│   ├── qa-env.py, check-playwright.py, make-gif.py
│   ├── guard-plan-context.py
│   ├── guard-fix-scope.py
│   └── guard-orchestrator.py
├── skills/
│   ├── generate-plan/, review-plan/, revise-plan/
│   └── update-progress/, update-changelog/, generate-changelog/
├── rules/
└── settings.json
```

The harness is described in more detail in `HARNESS_ARCHITECTURE.md`.

## What changed

**New agents**
- **orchestrator:** Coordinates the autonomous build; reads state through scripts and delegates every step.
- **git-manager:** Only commits (Haiku), keeping git out of the orchestrator.

**New skills/commands**
- **/run-orchestrator:** Runs the pre-flight checks and starts the orchestrator.
- **/escalate:** Re-scopes a failing increment with planner Mode 3 and clears BLOCKED.md.

**New scripts**
- **guard-orchestrator.py:** Restricts the orchestrator and git-manager to a predefined list of allowed commands, reducing the risk of the orchestrator bypassing the harness.
- **plan-edit.py:** Allows the planner to programmatically modify individual plan increments in modes 2 and 3, including replacing, appending, and other editing operations.
- **read-progress.py:** Reads only the required portions of the progress file programmatically. Supports distinct flags to retrieve the last specified entries and count retries, escalations, and deviations.
- **context-write.py:** Provides a structured way to write progress entries, update the changelog, and manage BLOCKED.md.
- **preflight-orchestrator.py:** Programmatically verifies the PLAN and PLAN_REVIEW statuses and checks that Git is properly configured before a run starts.
- **coverage-report.py:** Checks the 80% test coverage requirement against the JaCoCo report.
- **run-cost.py:** Reports token usage and associated costs for each agent.

# Workflow Run Summary

- Completed: all 16 increments passed their automated gates.
- Retries: 8 of 16 increments needed retries (50%).
- Mode 3 escalations: 0. I experienced one block, but the harness detected that it was unrelated to implementation or planning and escalated to a human instead of triggering Mode 3.
- Mode 2 forward validations: 8. 3 resulted in increment rewrites, while 5 confirmed the plan was still valid.
- Final gate: 317 backend tests pass, frontend lint and build pass, and coverage is 96.1% lines and 86.0% branches.

# Rough Cost

- Approximately $45 in total API costs.
- Around $25 came from Sonnet agents (implementation, refactor, and planner), according to `run-cost.py`.
- The remaining ~$20 came from Haiku agents and the main session.
- Average cost per increment: roughly $1.50-2.80.

# Optimisations that helped most

1. Reading only the slice an agent needs. read-increment.py, read-progress.py --last and check-increment-status.py give agents just their part of the plan and journal. A guard hook blocks reading PLAN.md and PROGRESS.md whole. Without this, each of the roughly 100 agent calls would re-read files that keep growing. This was the biggest saving.
2. Haiku for the cheap roles. code-review, test-runner and git-manager are the agents called most often, and Haiku costs a fraction of Sonnet.
3. Only test-runner runs the tests. implementation and refactor don't run Maven or npm, so build and test logs don't fill up the expensive Sonnet sessions.
4. Writing the PLAN and PROGRESS through scripts. context-write.py and plan-edit.py append or change a single section, so agents never rewrite the whole file.
5. Running review and tests in parallel. This saves time but not money.

# Key Takeaways

- I added scripts to read recent progress entries, count attempts, and detect deviations through structured keywords. This helped agents focus on relevant context and reduced token costs.
- I added a separate `git-manager` agent to handle local commits, keeping the orchestrator focused on high-level delegation.
- I found Sonnet to be a good choice for orchestration with a well-structured harness.

# Interesting Findings

- I added deviation checks to the pre-flight process to prevent unnecessary implementation retries. Although this might seem expensive, using specific keywords that scripts can easily detect keeps these checks affordable while improving Mode 2 drift detection.
- I consider that spent time manually reviewing the implementation plan, especially the interfaces and coding approach is worthwhile for reducing the need for mode 3 escalation in the orchestrator run.

# Challenges

I faced an initial issue during the first three increments that eventually raised a BLOCKED.md, but it was not implementation-related. I started the session using the --agent orchestrator flag, so my main session was already an orchestrator. Then when I called /run-orchestrator, it launched another orchestrator as a subagent but somehow it started its own workflow as well. Race conditions between both workflows made some writes impossible, eventually raising BLOCKED.md at the end of I3.

The solution was to remove `BLOCKED.md` without changing anything, since it was not an implementation or planning issue. I verified there were no overlapping workflows and called `/run-orchestrator` from a regular main-purpose agent, launching just one orchestrator as expected.