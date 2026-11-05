# Changelog

## [2026-11-05] — Orchestrated build

### Added
- Added JWT sign-in, a current-user endpoint, role-aware security, demo seed data, and a frontend login with cookie session and header.
- Added the core domain model, repositories and the appointment lifecycle transition table that drives the allowed actions shown in the UI.
- Added registration, availability, booking, appointment action, query and payment services, with a reservation expiry job and slot-level concurrency protection.
- Added the services catalog and the reception veterinarians page, with registration including a weekly schedule.
- Added owner and pet registration with a species-filtered breed picker and an owner "My pets" page.
- Added the walk-in booking page and the appointment detail page.
- Added online booking, where an owner reserves a slot and confirms it with a countdown before the reservation expires and is released.
- Added the owner appointments list and cancellation with a required reason.
- Added visit start, finish and observation editing for veterinarians and reception, and a veterinarian appointments page.
- Added the reception board of ongoing appointments with date and veterinarian filters.
- Added reception rescheduling and reassignment of appointments, with a linked new appointment.
- Added payments, appointment closing and a debts screen for reception.
- Added a pet service history page for reception and owners, with upcoming and past sections.
- Added loading and error states for the owner, veterinarian, reception and appointment routes.
- Added a full-lifecycle acceptance test with real sign-ins and rejection cases.

### Changed
- Reordered the plan into domain, services and then vertical slices, growing it from 14 to 16 increments.
- Included the owner pet history page in the badge and allowed-actions consistency pass.
- Bumped JaCoCo to 0.8.14.
- Cleaned up a misleading deviation note and added a missing test for the expiry reason.

### Fixed
- Fixed the concurrent booking test so it loads the application context and no longer collides with seeded services.
- Fixed the frontend build failing on the root layout session lookup.
- Bound the comma-separated status filter on the appointment list, and removed the unsupported default from the veterinarian list.
- Added a server-side receptionist role check to the reschedule page.
- Declared the cancel reason as required and at most 500 characters.

<!-- progress-entries: 111 -->
