---
name: update-progress
description: Record the step just completed in the project's progress journal. Use at the end of every planning or fix step (not for QA, plan-qa or verify steps).
---

# Update Progress

1. Open `context/PROGRESS.md`; if missing, create it with `# Progress`.
2. Append one entry per action:

   ```markdown
   ## YYYY-MM-DD — <action>
   - Status: <see the table below>
   - Details: <what was done, files touched, plan finding codes or bug IDs and titles, if any>

   ### Added
   - <what was introduced>

   ### Changed
   - <what was clarified or reworked>

   ### Fixed
   - <what was missing or wrong and is now correct>

   ### Removed
   - <what was dropped>
   ```

3. Change sections follow the `update-changelog` rules: one past-tense sentence per change, readable without opening the plan, the bugs or the QA report; omit empty sections.

| Action | Status | Details must include |
|---|---|---|
| `Plan generated` | plan generated | |
| `Review` | review verdict | |
| `Revision: <code> - <title>` | plan revised | |
| `Fix: <BUG-id> - <title>` | Fixed / In Progress | Root cause, solution, regression test name, test counts, blockers |
