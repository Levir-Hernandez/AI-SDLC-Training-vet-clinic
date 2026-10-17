---
name: update-progress
description: Record the step just completed in the project's progress journal. Use at the end of every planning step.
---

# Update Progress

1. Open `context/PROGRESS.md`; if missing, create it with `# Progress`.
2. Append one entry per action:

   ```markdown
   ## YYYY-MM-DD — <action>
   - Status: <plan generated | plan revised | review verdict>
   - Details: <what was done, files touched, finding codes and titles if any>

   ### Added
   - <what was introduced>

   ### Changed
   - <what was clarified or reworked>

   ### Fixed
   - <what was missing or wrong and is now correct>

   ### Removed
   - <what was dropped>
   ```

3. Change sections follow the `update-changelog` rules: one past-tense sentence per change, readable without opening the plan; omit empty sections.

Actions: `Plan generated`, `Review`, `Revision: <code> - <title>`.
