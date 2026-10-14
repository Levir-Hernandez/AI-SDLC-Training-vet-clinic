# Changelog

## [Unreleased] — 2026-10-14

### Added
- Created a frontend implementation plan with 12 ordered increments covering tooling and shared UI, sign-in, owners, pets, catalog, appointment list and booking, appointment detail with status actions, clinical data, payments, audit log, search with medical history, and final polish.
- Added the first frontend plan review with one major and four minor findings.
- Added a test requirement that create-pet and payment requests send uppercase enum values.
- Added a pet card fallback that shows history only with a "Deleted" tag when the pet itself returns 404, with a test for it.

### Changed
- Marked the `allowedActions` vocabulary as defined by the backend plan instead of an unconfirmed assumption.

### Fixed
- Declared the exact backend wire values (`ADMINISTRATOR`, `VETERINARIAN`, `DOG`, `MALE`, `CASH`, audit types, statuses) in the shared types and noted that plan labels are display shorthand.
- Aligned the deleted-pet assumption so the appointment header links a deleted pet to its history.
- Set the appointments list increment to depend on Increment 2 and Backend Increments 2, 4 and 6.
- Noted that the open-appointment owner delete error is mocked in tests and verified manually after Backend Increment 6.
