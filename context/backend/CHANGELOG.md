# Changelog

## [Unreleased] — 2026-10-14

### Changed
- Corrected assumption A5 to state that partial payments can exist on a READY appointment.
- Defined `allowedActions` as an enum of exactly ten values with the status and role rules that produce each one.
- Defined paging as zero-based with default size 20 and maximum 100 (clamped), and `fieldErrors` as a flat map keyed by request property name, with tests in `GlobalExceptionHandlerTests`.
- Defined the appointment summary with ids and deleted flags for pet and owner and with vet id and name, matching the detail header objects.

- Listed TC-6, AC-23 and every UF code in the covered lines of the increments that deliver them, and aligned the coverage matrix.

### Fixed
- Removed the ambiguity the frontend had about which action names gate its buttons and sections, and added a per-status and per-role unit test.
- Blocked rollback from READY with a 409 ROLLBACK_BLOCKED_BY_PAYMENTS error when a payment exists, so the total cannot fall below the amount paid, and added the matching test.

### Added
- Created a backend implementation plan with 12 ordered increments from shared error handling and authentication to owners, pets, catalogs, appointments, state machine, payments, audit log, search and hardening.
- Added the API contract section listing every endpoint with request, response, errors and roles.
- Recorded a first plan review with one major and six minor findings.
