---
name: update-progress
description: Appends an entry to the calling agent's PROGRESS.md (execution journal). Append only; never edits or deletes entries.
---

# Update Progress

| Agent | Folder |
|---|---|
| `backend-planner` | `context/backend/` |
| `frontend-planner` | `context/frontend/` |
| `integration-planner` | `context/integration/` |

1. Open `PROGRESS.md` in your folder; if missing, create it with `# Progress`.
2. Append one entry per action:

   ```markdown
   ## YYYY-MM-DD — <agent or area> — <action>
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

Actions: `Plan generated`, `Validation`, `Revision: <code> - <title>`, `Integration check`.
