## Solution Summary

```
.claude/
├── agents/
│   ├── planner.md
│   ├── plan-reviewer.md
│   └── implementation.md
├── commands/
│   └── implement-increment.md
├── scripts/
│   ├── read-increment.py
│   ├── check-increment-status.py
│   ├── check-plan-review-status.py
│   ├── update-increment-status.py
│   └── guard-plan-context.py
├── skills/
│   ├── generate-plan/
│   ├── review-plan/
│   ├── revise-plan/
│   ├── update-progress/
│   └── update-changelog/
└── rules/
    ├── java-coder.md
    ├── java-tester.md
    ├── react-coder.md
    └── react-tester.md
```

Plan Loop: `/generate-plan` → `/review-plan [approach]` → while the verdict is not `READY`: `/revise-plan [codes] [-- context]` → `/review-plan`.

The harness is described in more detail in `HARNESS_ARCHITECTURE.md`.

## Key Takeaways

- Each increment gets its own commit. For now, I manually verify that the implementation matches the increment's description but also a code reviewer agent could review the diffs, identify issues, and flag discrepancies between the implementation and the expected outcome.
- Isolating each increment's context with deterministic scripts, instead of letting the agent read the whole plan, can save tokens.
- In each increment, don't fully trust the agent's report: review the diff and check for false positives.
- A fine-grained plan lets cheaper models like Sonnet implement without the risk of making architectural decisions.
- Token savings can come from tight context or from output compression (RTK), but combining both may hurt code generation.

## Challenges Encountered

- Token economics and context management were the main challenges in this phase. There are many approaches, each with its own benefits; I opted for Python scripts that control access to each increment's context in isolation.
- With the Read and Write tools used so heavily on the repository, blocking them completely isn't practical. I added a hook, `guard-plan-context.py`, that blocks direct tool access to `PLAN.md` and `PLAN_REVIEW.md` while leaving every other file accessible. For those two files, it enforces deterministic scripts to read and update only what's needed:
  - `read-increment.py <n>`
  - `check-increment-status.py <n>`
  - `check-plan-review-status.py <n>`
  - `update-increment-status.py <n> <status>`
- While supervising an `implement-increment` run, I noticed the Write tool sometimes hits EOF errors that keep changes from being applied. Usually the model catches the error and retries or tries another approach, mostly harmless apart from the extra token usage. In one case, though, the increment finished without any of the expected frontend work: the call failed, the model ignored the error and continued as if the files had been written. The tests didn't catch it either, because that functionality had no tests yet, so the suite passed and gave a false positive.

## Interesting Findings

- Splitting the plan into per-increment files helps manage context, but it introduces serious synchronization problems, so I decided scripts were the better approach.
- Cheaper models like Sonnet give good results in this phase, but the implementation plan should be refined and specified at a fine-grained level, so Sonnet isn't pushed to make complex architectural decisions about the code.
- Since the context is already tight (one increment at a time, not the whole plan) and there's no centralized track of the complete implementation phase, RTK to cut token usage on Bash, Write and Read probably won't help much with code generation. An inverse strategy could get similar results and token costs: broader context strategies plus RTK to compress the cost of Bash, Write and Read calls.
