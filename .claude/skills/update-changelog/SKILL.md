---
name: update-changelog
description: Updates the calling agent's CHANGELOG.md from its new PROGRESS.md entries, in Keep a Changelog style (Added, Changed, Fixed, Removed).
---

# Update Changelog

Same folder as `update-progress`.

1. Read `PROGRESS.md` and `CHANGELOG.md`; if the changelog is missing, create it with `# Changelog`.
2. Take the PROGRESS entries not yet reflected in the changelog.
3. Collect their **Added / Changed / Fixed / Removed** items ([Keep a Changelog](https://keepachangelog.com) conventions). Merge duplicates and drop items a later entry undid.
4. Merge them into the `[Unreleased]` section and update its date. Omit empty categories.

```markdown
# Changelog

## [Unreleased] — YYYY-MM-DD

### Added
- Created an implementation plan with 8 ordered increments covering setup, owners and pets, appointments and payments.

### Changed
- Clarified which fields the appointment form receives on first display.

### Fixed
- Added the missing CORS configuration required by the frontend.
```

- One sentence per change, past tense, readable without opening the plan.
- No status or verdict lines; those belong in `PROGRESS.md`.
