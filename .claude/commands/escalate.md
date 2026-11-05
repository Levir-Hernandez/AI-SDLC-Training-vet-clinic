---
description: Re-scope a failing or blocked increment with planner Mode 3, then clear BLOCKED.md. Used by the orchestrator's circuit breaker, or by hand after a halt.
argument-hint: "[increment id, e.g. I7] [retry history]"
---

Escalate `$ARGUMENTS`. The first word is the increment id; the rest is its retry history.

1. Run `python .claude/scripts/read-progress.py --escalations <id>`. If it is already 2, do not escalate again: write `Increment <id> was escalated twice and still fails; it needs a human decision.` plus the history with `python .claude/scripts/context-write.py write BLOCKED.md`, reply with it and stop.
2. If `context/BLOCKED.md` exists, read it: it is part of the history. Without history in the arguments, add `python .claude/scripts/read-progress.py --last 6`.
3. Invoke the `planner` agent with: `Mode 3`, the increment id and the full history.
4. If the planner replies `NEEDS HUMAN:`, write that reason with `python .claude/scripts/context-write.py write BLOCKED.md`, reply with it and stop.
5. Otherwise remove the semaphore: `python .claude/scripts/context-write.py delete BLOCKED.md`.
6. Reply with the planner's decision and the increment to resume at. After a manual escalation, the next step is `/run-orchestrator`.
