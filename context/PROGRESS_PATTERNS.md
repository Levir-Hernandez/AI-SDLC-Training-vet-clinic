# Progress Patterns

The entry shapes every agent appends to `PROGRESS.md`. Every agent writes its own entry at the end of its step; nobody writes an entry for another agent. Append with `python .claude/scripts/context-write.py append PROGRESS.md` and `{now}` as the timestamp; read with `read-progress.py`.

```markdown
## [{now}] — <Type> — <Label>
- agent: <agent-name>
- increment: <id, or - when there is none>
- summary: <short delta>
- <fields of the type>
```

Then, only when something changed for the reader: `### Added`, `### Changed`, `### Fixed`, `### Removed`, one past-tense sentence per item.

| Type | Written by | Label | Extra fields (all required) |
|---|---|---|---|
| `Increment` | implementation | `<id> attempt <n>` | `attempt: <n>`, `files_created: [...]`, `files_modified: [...]`, `interfaces_created: [...]`, `decisions: [...]`, `deviations: none or [...]`, `tests: X passed / Y failed`, `blockers: none or [...]` |
| `CodeReview` | code-review | `<id> attempt <n>` | `attempt: <n>`, `result: PASS, FAIL or ERROR`, `critical_high: none or [file:line what]`, `medium_findings: none or key, key`, `files_reviewed: [...]` |
| `TestRun` | test-runner | `<id> attempt <n>` | `attempt: <n>`, `mode: increment or final`, `result: PASS, FAIL or ERROR`, `tests: X passed / Y failed`, `coverage: <line>, <branch>`, `failures: none or [...]` |
| `Validation` | planner Mode 2 | `before <id>` | `trigger: deviations or cadence`, `rewritten: none or [ids]` |
| `Escalation` | planner Mode 3 | `<id>` | `diagnosis: <one line>`, `decision: split or approach`, `resume_at: <id>` |
| `Refactor` | refactor | `<finding key>` | `files_modified: [...]`, `tests: X passed / Y failed` |
| `Plan` | planner Mode 1 | `generated` or `revision <codes>` | `status: <plan status>` |
| `Review` | plan-reviewer | `pass <n>` | `status: <verdict>` |
| `Fix` | refactor | `<BUG-id> <title>` | `status: Fixed or In Progress`, `root_cause`, `solution`, `regression_test`, `tests`, `blockers` |

- An increment attempt passes its gate when its `CodeReview` and its `TestRun` both say `result: PASS`, and fails when either says `FAIL`. `ERROR` is not a gate: the orchestrator runs that agent again for the same attempt. `read-progress.py` works this out; nobody writes a separate gate entry.
- `code-review` and `test-runner` runs after a refactor use `increment: -`; the final gate uses `increment: final`. Neither counts as an increment attempt.
- `context-write.py` refuses an entry whose type is unknown or that misses a required field, and names what is missing.
- `read-progress.py` counts on the exact type names and the `increment:`, `attempt:`, `result:`, `medium_findings:` and `deviations:` fields. Keep every value on its one line.
