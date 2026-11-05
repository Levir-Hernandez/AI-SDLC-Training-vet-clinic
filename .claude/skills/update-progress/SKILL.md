---
name: update-progress
description: Record the step just completed in the project's progress journal. Use at the end of every planning, build or fix step (not for QA, plan-qa or verify steps).
---

# Update Progress

1. Read `context/PROGRESS_PATTERNS.md` once, and take the entry shape of your step's Type.
2. Append one entry per action, never reading or rewriting the journal:

   ```bash
   python .claude/scripts/context-write.py append PROGRESS.md <<'ENTRY'
   ## [{now}] — Increment — I5 attempt 1
   - agent: implementation
   - increment: I5
   - attempt: 1
   - summary: Reception can register veterinarians with a weekly schedule.
   - files_created: [VeterinarianService, VeterinarianController, VeterinarianServiceTests]
   - files_modified: [SecurityConfig]
   - interfaces_created: [POST /api/veterinarians, VeterinarianService.register]
   - decisions: [schedule blocks validated in the service, not the controller]
   - deviations: none
   - tests: 42 passed / 0 failed
   - blockers: none

   ### Added
   - Added veterinarian registration with a weekly schedule.
   ENTRY
   ```

3. Every field of your Type in `PROGRESS_PATTERNS.md` is required; `context-write.py` refuses an entry that misses one and names it. Add the missing fields and append again.
4. Change sections follow the `update-changelog` rules: one past-tense sentence per change, readable without opening the plan, the bugs or the QA report; omit empty sections.
