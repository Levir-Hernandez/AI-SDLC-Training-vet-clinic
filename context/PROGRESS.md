# Progress

## 2026-10-17 — Plan generated
- Status: plan generated
- Details: Created `context/PLAN.md` with 14 pending increments (I1–I14) on top of skeleton I0. No prior plan or review existed, so no increments were preserved and no findings were addressed. The revise-plan request was not applicable because `context/PLAN_REVIEW.md` does not exist yet.

### Added
- Created an implementation plan with 14 ordered increments covering authentication, veterinarians and services, owners and pets, availability, walk-in and online booking, cancellation, visits, the reception board, rescheduling, payments and debts, pet history, badge consistency and a final acceptance pass.
- Defined a single lifecycle component that owns the full transition table and drives the allowed actions shown in the UI.
- Specified slot-level concurrency protection through a veterinarian row lock and an overlap re-check at booking.
- Recorded assumptions A1–A7 covering owner and veterinarian accounts, seed credentials and schedules, time zone and slot grid, debt definition, reassignment totals and the Secure cookie on localhost.

## 2026-10-17 — Review
- Status: READY WITH MINOR CHANGES (pass 1)
- Details: Wrote `context/PLAN_REVIEW.md`. Majors: M1 Increment order does not establish domain and use cases before vertical slices; M2 Appointment response and filters use debt before payments exist. Minors: m1 Status filter parameter binding is never assigned to an increment; m2 Pet history endpoint mapped in AppointmentController under a different path prefix; m3 Reschedule page has no receptionist guard; m4 Owner history endpoint has no frontend consumer.

### Added
- Added the first plan review with two major and four minor findings.

## 2026-10-17 — Revision: M1 - Increment order does not establish domain and use cases before vertical slices
- Status: plan revised
- Details: Rewrote `context/PLAN.md` into 16 pending increments: I1 core domain model and lifecycle, I2 registration use cases, I3 appointment and payment use cases, then I4–I16 as vertical slices (auth and seed, veterinarians, owners and pets, walk-in, online booking, owner cancel, visit, board, reschedule, payments, history, badges, acceptance). Updated both summary tables, every Covers and Depends on, and added assumption A8. Marked M1 `fixed` in `context/PLAN_REVIEW.md`. Side effect: because `Payment` and the debt computation now exist from I1/I3, the forward reference in M2 no longer applies, but M2 was left `open` for the reviewer to confirm. m1–m4 were not addressed.

### Added
- Added a core domain increment that creates every entity, enum and repository together with the appointment lifecycle table and its unit and JPA tests.
- Added two application-service increments that build the registration use cases and all appointment and payment use cases in their final shape, tested with a fixed clock and a service-level concurrency test.
- Added an assumption that the first three increments are backend-only and change nothing visible.

### Changed
- Reordered the plan so authentication, seed data, controllers and screens come as vertical slices after the domain and service layers, growing the plan from 14 to 16 increments.
- Changed the slice increments so they only add controllers, security wiring and screens on top of the existing services, with slice and integration tests instead of service tests.
- Moved the full demo seed (receptionist, veterinarians and services) into the authentication increment.

### Fixed
- Fixed the piecemeal design of the booking and action services, including the late extraction of the shared scheduled-booking method used by rescheduling.

## 2026-10-17 — Review
- Status: READY (pass 2)
- Details: Rewrote `context/PLAN_REVIEW.md`. Resolved: Increment order does not establish domain and use cases before vertical slices; Appointment response and filters use debt before payments exist. Open minors: m1 Status filter parameter binding is never assigned to an increment; m2 Pet history endpoint mapped in AppointmentController under a different path prefix; m3 Reschedule page has no receptionist guard; m4 Owner history endpoint has no frontend consumer; m5 CancelRequest validation never defined but I9 expects VALIDATION_FAILED.

### Added
- Added a second plan review that confirmed the layered increment order and flagged an undefined validation rule on the cancel request.

### Fixed
- Confirmed that the debt flag is now computed only after payments exist, so it no longer refers to a later increment.

## 2026-10-17 — Revision: m1 - Status filter parameter binding is never assigned to an increment
- Status: plan revised
- Details: `context/PLAN.md` I10 Files, Tasks, Expected Interfaces and Tests; Backend API Summary. Marked m1 fixed in `context/PLAN_REVIEW.md`.

### Fixed
- Bound the comma-separated `status` query parameter on the appointment list in I10, the first increment that uses it, with slice tests for valid and unknown values.

### Changed
- Listed every supported appointment list query parameter and the increment that adds it in the Backend API Summary.

## 2026-10-17 — Revision: m3 - Reschedule page has no receptionist guard
- Status: plan revised
- Details: `context/PLAN.md` I12 Frontend Create and Tasks. Marked m3 fixed in `context/PLAN_REVIEW.md`.

### Fixed
- Added a server-side receptionist role check to the reschedule page.

## 2026-10-17 — Revision: m5 - CancelRequest validation never defined but I9 expects VALIDATION_FAILED
- Status: plan revised
- Details: `context/PLAN.md` I3 DTO list and I9 CancelDialog task. Marked m5 fixed in `context/PLAN_REVIEW.md`.

### Fixed
- Declared the cancel request reason as required and at most 500 characters, so a blank reason over HTTP returns a validation error while the lifecycle check remains as a service-level guard.
- Aligned the cancel dialog to display the same validation error the API returns.

## 2026-10-17 — Review
- Status: READY (pass 3)
- Details: Rewrote `context/PLAN_REVIEW.md`. Resolved: Status filter parameter binding is never assigned to an increment; Reschedule page has no receptionist guard; CancelRequest validation never defined but I9 expects VALIDATION_FAILED. Open minors: m1 Pet history endpoint mapped in AppointmentController under a different path prefix; m2 Owner history endpoint has no frontend consumer. No new findings.

### Added
- Added a third plan review that confirmed the status filter binding, the reschedule page guard and the cancel validation fixes.

## 2026-10-17 — Revision: m2 - Owner history endpoint has no frontend consumer
- Status: plan revised
- Details: Applied user context (owners may view their own pets' history; reception screen unchanged). `context/PLAN.md`: new assumption A9, Backend API Summary roles note, Frontend Views Summary row, I14 Goal/Covers/Files/Tasks/Expected/Completion, I15 page list. Marked m2 fixed and logged the decision in `context/PLAN_REVIEW.md`.

### Added
- Added an owner pet history page reached from a History link per pet on the owner's pets list, using the existing pet history endpoint and showing a not-found page for another owner's pet.
- Recorded an assumption that owners may view their own pets' service history, since the task lists only reception history.

### Changed
- Included the owner pet history page in the badge and allowed-actions consistency pass.

## 2026-10-17 — Review
- Status: READY (pass 4)
- Details: Rewrote `context/PLAN_REVIEW.md`. Resolved: Owner history endpoint has no frontend consumer. Open minors: m1 Pet history endpoint mapped in AppointmentController under a different path prefix; m2 Veterinarian list promises a date default that no parameter supports yet (new); m3 AllowedActionsResponseTests described as a slice with a mocked repository (new).

### Added
- Added a fourth plan review that confirmed the owner pet history view and flagged an unsupported date default on the veterinarian list and a mislabelled allowed-actions test.

## 2026-10-17 — Revision: m3 - AllowedActionsResponseTests described as a slice with a mocked repository
- Status: plan revised
- Details: Updated I15 Implementation Tasks and Automated Tests in `context/PLAN.md` so the test is an integration test (`@SpringBootTest` + MockMvc, `TestJwt`, data inserted through repositories); marked m3 fixed in `context/PLAN_REVIEW.md`.

### Fixed
- Reclassified the allowed-actions response test in the badge consistency increment as an integration test, so the real query service and lifecycle produce the actions it checks.

## 2026-10-17 — Revision: m2 - Veterinarian list promises a date default that no parameter supports yet
- Status: plan revised
- Details: Updated the I10 Frontend Create entry and Implementation Tasks for `frontend/app/vet/appointments/page.tsx` in `context/PLAN.md` to drop the "today and upcoming" default and rely on the status filter plus the backend `startAt` ascending sort; marked m2 fixed in `context/PLAN_REVIEW.md`.

### Fixed
- Removed the unsupported today-and-upcoming default from the veterinarian appointment list, which now relies on the non-final status filter and start-time ordering.

## 2026-10-17 — Review
- Status: READY (pass 5)
- Details: Rewrote `context/PLAN_REVIEW.md`. Resolved: Veterinarian list promises a date default that no parameter supports yet; AllowedActionsResponseTests described as a slice with a mocked repository. Open minors: m1 Pet history endpoint mapped in AppointmentController under a different path prefix; m2 PetHistoryTests slice cannot prove ordering and scoping (new); m3 Manual TTL override uses an environment variable that may not bind (new).

### Added
- Added a fifth plan review that confirmed the veterinarian list and allowed-actions test fixes, and flagged a weak pet history slice test and a reservation TTL override that may not take effect in the manual expiry check.

## 2026-10-19 — Increment 1: Core domain model and appointment lifecycle
- Status: completed

### Behavior delivered
- Added every domain entity, enum and repository (`UserAccount`, `ClinicService`, `Veterinarian` with `ScheduleBlock`, `Owner`, `Pet`, `Appointment`, `Payment`, `Breed`/`Species`), the `DomainException` family, `PageResponse` and the `Clock` bean. The H2 schema is created from them and the backend still starts (`/api/health` returned 200).
- `AppointmentLifecycle` owns the full TASK.md transition table (12 rows including EXPIRE), role checks, required cancel reason, reservation expiry check on CONFIRM, observation edit window and `allowedActions`.
- `ScheduleValidator` and the `Pet` breed/species check enforce their invariants; `AppointmentRepository` has the overlap, blocking-day, expiry and reschedule-link queries.

### Decisions and deviations
- Completion criteria (all met): `./mvnw verify` BUILD SUCCESS; `npm run lint` clean; `npm run build` succeeds; backend started with `spring-boot:run` and `GET /api/health` returned 200. The plan file has no checkboxes and is guarded from direct edits, so criteria are recorded here and the status was set via script.
- `UserAccount.username` and `Owner.phone` use `@Column(unique = true)` (which creates the index) instead of a separate `@Index`, to avoid a duplicate index.
- `RESCHEDULED` is treated as final in `AppointmentStatus.isFinal()`, so observations are not editable there (as the plan states).
- Overlap messages use an ASCII hyphen (`09:00-13:00`) rather than an en dash.
- Review findings: none target I1 (check-plan-review-status reported no open findings for I1).
- Ran `npm install` in `frontend/` because `node_modules` was missing; no frontend source changed.

### Tests
- Backend: 155 run, 0 failed, 0 skipped (AppointmentLifecycleTests 127, AppointmentRepositoryTests 13, ScheduleValidatorTests 8, BreedTests 5, plus the existing health and context tests). Frontend lint and build pass.

### Blockers
- None
