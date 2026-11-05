---
name: generate-changelog
description: Write the final CHANGELOG entry that summarises a completed orchestrator run. Use once, after the last increment passes its gates.
---

# Generate Changelog

1. Run `python .claude/scripts/read-progress.py --since-changelog` and `git log --oneline`. Read `context/CHANGELOG.md`. Its `[Unreleased]` section already summarises every passed increment, because `update-changelog` ran after each one; the script prints only the entries since then.
2. From those new entries, keep what this run built: `Increment` entries whose attempt passed both `CodeReview` and `TestRun`, plus `Refactor` and `Escalation` entries. Ignore failed attempts.
3. Promote `[Unreleased]` and those entries into one dated section at the top, under `# Changelog`, in [Keep a Changelog](https://keepachangelog.com) form. No `[Unreleased]` section remains:

```markdown
# Changelog

## [<YYYY-MM-DD>] — Orchestrated build

### Added
- Added online booking, where an owner reserves a free slot and confirms it before the reservation expires.

### Changed
- Split the appointment workflow into smaller steps after repeated failures.

### Fixed
- Fixed the second booking of a slot, which is now rejected with fresh availability.
```

4. Replace the marker at the end of the file with the new one from step 1. Save the whole file with `python .claude/scripts/context-write.py write CHANGELOG.md`, keeping earlier sections unchanged.

- One sentence per change, past tense, describing behavior a user of the app could observe; readable without opening the plan.
- Describe only what the code really does. Merge duplicates and drop anything a later entry undid.
- No status, retry counts or verdict lines; those belong in `PROGRESS.md`.
