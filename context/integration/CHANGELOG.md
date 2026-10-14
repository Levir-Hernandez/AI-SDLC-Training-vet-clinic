# Changelog

## [Unreleased] — 2026-10-14

### Added
- Created an integration plan with 8 ordered increments covering startup, authentication and CORS, registry and catalog, booking, visit lifecycle, payments, audit/search/history and a final containerized run.
- Added a shared configuration table with ports, URLs, variables, seed accounts and wire formats.
- Listed 7 suggested adjustments for pending backend and frontend increments: allowedActions values, paging and fieldErrors shape, appointment summary fields, pet and owner summaries, employee role, enum wire values and catalog dropdown size.
- Created the integration plan review with one major and four minor findings.

### Changed
- Moved the browser "backend reachable, no CORS error" check from Increment 1 to Increment 2, where credentialed CORS exists.
- Added Backend Increment 9 to the dependencies of Increment 5 and noted that the payments API exists by then while its flow is verified in Increment 6.

### Fixed
- Closed the finding about SA4 naming two increments, since the adjustment no longer exists in the integration plan.

### Removed
- Removed the suggested adjustments section from the integration plan after moving its seven items into the backend and frontend reviews as findings.
