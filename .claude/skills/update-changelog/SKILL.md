---
name: update-changelog
description: Summarize recent progress into the project changelog. Use right after updating progress.
---

# Update Changelog

1. Run `python .claude/scripts/read-progress.py --since-changelog`. Its first line is the marker `<!-- progress-entries: N -->`; the rest are only the PROGRESS entries the changelog does not reflect yet, so the journal is never reread whole. Read `context/CHANGELOG.md`; if it is missing, start it with `# Changelog`.
2. From those entries, skip `Increment` entries of attempts whose `CodeReview` or `TestRun` says `result: FAIL`. Review and test entries themselves carry no changelog items.
3. Collect their **Added / Changed / Fixed / Removed** items ([Keep a Changelog](https://keepachangelog.com) conventions). Merge duplicates and drop items a later entry undid.
4. Merge them into the `[Unreleased]` section and update its date. Omit empty categories. Replace the marker at the end of the file with the new one from step 1 (add it if there is none). Save the whole file with `python .claude/scripts/context-write.py write CHANGELOG.md`.

```markdown
# Changelog

## [Unreleased] — YYYY-MM-DD

### Added
- Created an implementation plan with 18 ordered increments covering authentication, owners and pets, appointments and payments.

### Changed
- Reordered the payment increments so they come after the appointment lifecycle.

### Fixed
- Added the missing rejection tests for cancelling without a reason.

<!-- progress-entries: 14 -->
```

- One sentence per change, past tense, readable without opening the plan.
- No status or verdict lines; those belong in `PROGRESS.md`.
- Keep the marker as the last line. It is an HTML comment, so it does not show when the changelog is rendered.
- In an orchestrator run, `planner` uses this once at Finalize, right before `generate-changelog` promotes `[Unreleased]`.
