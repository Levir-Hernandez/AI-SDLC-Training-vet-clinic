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

## 2026-10-19 — Increment 2: Registration use cases
- Status: completed

### Behavior delivered
- Service-layer registration: `VeterinarianService` (create with schedule validation and linked `VETERINARIAN` account, paginated list, get/require), `OwnerService` (create with unique phone, `OWNER` account using phone as username, optional initial pets; search list; detail with pets), `PetService` (add, owner/receptionist/vet scoped `listFor`, `requireAccessible`), `AccountService` (BCrypt, `USERNAME_TAKEN`), `CatalogService`, `CurrentUser` record and `PasswordConfig` BCrypt bean, plus request/response DTOs. No endpoints are exposed yet (A8).

### Decisions and deviations
- Completion criteria (all met): `./mvnw verify` passes; `npm run lint` clean; `npm run build` succeeds; backend started and `GET /api/health` returned 200 (frontend unchanged). The plan file is guarded from direct edits, so criteria are recorded here and the status was set via script.
- `OwnerService.create` builds and validates all initial pets (via package-private `PetService.build`) before persisting anything, so a mismatched breed saves nothing.
- Added repository methods `OwnerRepository.existsByPhone`/`search` and `PetRepository.findByOwnerId`/`findByOwnerIdOrderById` (not listed in the plan, needed by the services).
- Receptionist listing pets without `ownerId` raises `400 OWNER_ID_REQUIRED`; vet access raises `403 FORBIDDEN`.
- Review findings: none target I2.

### Tests
- Backend: 171 run, 0 failed, 0 skipped (new: VeterinarianServiceTests 4, OwnerServiceTests 3, PetServiceTests 9). Frontend lint and build pass.

### Blockers
- None

## 2026-10-19 — Increment 3: Appointment and payment use cases
- Status: completed

### Behavior delivered
- Service-layer appointment and payment use cases (no endpoints yet, A8): `AvailabilityService` (15-minute slots inside schedule blocks, future only, ignoring non-blocking statuses), `BookingService` (receptionist walk-in to SCHEDULED, owner reservation to RESERVED with `reservedUntil = now + ttl`, vet row lock plus overlap re-check giving `SLOT_TAKEN`), `AppointmentActionService` (confirm, cancel, start, finish, close, observations, reschedule/reassign, `expireDue`), `PaymentService` (exact-total single payment), `AppointmentQueryService` (scoped list with status/date/vet/debt filters, scoped get, pet history, response mapping with `allowedActions`, `debt`, `upcoming`), plus `ReservationExpiryJob`, `SchedulingConfig` and the `app.booking.*` settings.

### Decisions and deviations
- Completion criteria (all met): `./mvnw verify` BUILD SUCCESS; `npm run lint` clean; `npm run build` succeeds; backend started, `GET /api/health` returned 200 and the log showed no errors with the expiry sweeper running. The plan file is guarded from direct edits, so criteria are recorded here and the status was set via script.
- Added `AppointmentLifecycle.validate(...)` (same checks as `apply`, no mutation; `apply` now shares the same private resolver). Reschedule and pay validate first, secure the new slot or check the amount, and only then apply, so a conflict leaves the original unchanged even in unit tests.
- `AppointmentRepository` now also extends `JpaSpecificationExecutor` and gained `findByPetId(Long, Pageable)` for the dynamic scoped list and history (not listed in the plan).
- `AppointmentQueryServiceTests` is a `@DataJpaTest` (real repositories) instead of a mocked unit test, because scoping and the debt filter are JPA specifications that mocks cannot verify. Expired-RESERVED/CANCELLED non-blocking rules in `AvailabilityServiceTests` rely on `findBlocking`, which is proven in `AppointmentRepositoryTests`; the unit test verifies the query is called with the current time.
- `BookingConcurrencyIntegrationTests` uses the default `@SpringBootTest` (mock web environment) because `webEnvironment = NONE` fails to start with `SecurityConfig`.
- `RescheduleRequest.veterinarianId` is optional (keeps the original vet when absent); reschedule new appointments are `bookedBy` RECEPTIONIST.
- Review findings: none target I3 (check-plan-review-status: no open findings for I3).

### Tests
- Backend: 254 run, 0 failed, 0 skipped (new: AvailabilityServiceTests 14, BookingServiceTests 9, AppointmentActionServiceTests 37, AppointmentQueryServiceTests 14, PaymentServiceTests 8, BookingConcurrencyIntegrationTests 1). Frontend lint and build pass.

### Blockers
- None

## 2026-10-19 — Increment 4: Authentication, roles and demo seed
- Status: completed

### Behavior delivered
- Backend: `POST /api/auth/login` issues an HS256 JWT (`sub`, `role`, `ownerId`/`veterinarianId`, 8 h expiry); `GET /api/auth/me` returns the caller. All other endpoints except health, Swagger/OpenAPI and actuator health require a valid token; 401/403 are `application/problem+json` with `code`; a single `GlobalExceptionHandler` maps domain, validation (`VALIDATION_FAILED` with `errors[]`), access-denied and unexpected errors. `CurrentUser.from(Authentication)`, `@EnableMethodSecurity`, OpenAPI bearer scheme and `DemoDataSeeder` (receptionist, two vets with accounts and Mon-Fri schedules, four services; idempotent) are in place.
- Frontend: `/login` page with server action `signIn`, token stored only in the `vc_session` HttpOnly/Secure/SameSite=Lax cookie, `proxy.ts` redirect to `/login`, `requireRole`/`getCurrentUser`, server-only typed API layer (`ApiError` with field errors), `AppHeader` with role and sign-out, shared `FormField` and `ErrorMessage`, role welcome on `/`. `backend-status.tsx` removed.

### Decisions and deviations
- Completion criteria (all met): `./mvnw verify` BUILD SUCCESS; `npm run lint` clean; `npm run build` succeeds. Live check with both apps (curl): `/` redirects to `/login`; `/api/auth/me` without token gives 401 problem+json; sign-in as `reception` via the real server action returned 303 to `/` and set `vc_session` flagged HttpOnly and Secure (cookie jar), and the home page then rendered the header with Sign out. Not exercised: browser DevTools view and the `vet.ana` sign-in via the UI (covered by `AuthFlowIntegrationTests` on the API). The plan file is guarded, so criteria are recorded here and the status set via script.
- `TokenService.issue` returns `IssuedToken(value, expiresAt)` instead of a bare `String`, so login can return `expiresAt` for the cookie expiry.
- `CurrentUserResponse` also carries `displayName` (used by the header).
- Seeding is switchable with `app.seed.enabled` (default true). Tests set it to false in `src/test/resources/application.properties` (together with a test JWT secret) because several tests save services/vets with unique codes in the shared in-memory H2; `AuthFlowIntegrationTests` and `DemoDataSeederTests` construct and run the seeder explicitly.
- JWT expiry is validated against the injected `Clock` with zero skew. 401/403 filter responses are written by `JsonAuthHandlers` (plain class built in `SecurityConfig`) from a map, to avoid depending on ProblemDetail mixins in the security filter chain.
- `AppHeader` nav currently only has "Home" per role; later increments add their links (avoids dead links). `app/page.tsx` shows a welcome because the role routes do not exist yet. `Suspense` boundaries wrap cookie-reading components because `cacheComponents` is enabled. On a backend 401 during render, `apiFetch` tries to clear the cookie (only possible in actions) and redirects to `/login`.
- `server-only` resolves through Next's bundled alias; no dependency added. `HealthControllerTests` now also imports `ClockConfig`.
- Review findings: none target I4 (3 open minors elsewhere).

### Tests
- Backend: 267 run, 0 failed, 0 skipped (new: AuthControllerTests 6, AuthFlowIntegrationTests 5, DemoDataSeederTests 2). Frontend: lint and build pass; no frontend test suite.

### Blockers
- None

## 2026-10-19 — Increment 5: Services catalog and veterinarian registration
- Status: completed

### Behavior delivered
- Backend: `GET /api/services` (any authenticated role, unpaginated list of the four services); `GET /api/veterinarians` (paginated, default size 20 sorted by name), `GET /api/veterinarians/{id}` and `POST /api/veterinarians` (receptionist only, `@Valid`, 201 + `Location`). Invalid schedules (`INVALID_SCHEDULE`), negative cost (`VALIDATION_FAILED`) and duplicate username (`USERNAME_TAKEN`) come from the existing services and handler.
- Frontend: `/reception/veterinarians` lists veterinarians (name, specialization, cost, schedule summary) with loading, empty and error states and pagination, plus a register form with dynamic schedule rows, a client check that start is before end, and backend field and schedule errors shown next to their fields. `app/reception/layout.tsx` enforces the receptionist role server-side (others redirect to `/`). New shared `EmptyState` and `Pagination`; "Veterinarians" link in the receptionist header; new types in `lib/types.ts`.

### Decisions and deviations
- Completion criteria: `./mvnw verify` passes, `npm run lint` clean, `npm run build` succeeds (all met). The manual UI check in a browser with both apps running was not performed in this session; behavior is covered by controller slice tests and the build. The plan file is guarded, so criteria are recorded here and the status set via script.
- `CreateVeterinarianRequest` TypeScript type allows `costPerService: null` so an empty cost field reaches the backend as a `VALIDATION_FAILED` field error instead of `0`.
- `reception/layout.tsx` wraps the guard in `Suspense` because `cacheComponents` is enabled. The create action redirects back to the list after success (revalidating the path).
- Review findings: none target I5 (3 open minors elsewhere).

### Tests
- Backend: 277 run, 0 failed, 0 skipped (new: VeterinarianControllerTests 8, CatalogControllerTests 2). Frontend: lint and build pass; no frontend test suite.

### Blockers
- None

## 2026-10-19 — Increment 6: Owners and pets registration with breed picker
- Status: completed

### Behavior delivered
- Backend: `POST/GET /api/owners`, `GET /api/owners/{id}`, `POST /api/owners/{id}/pets` (receptionist only, 201 + `Location`, `?q=` name/phone search, default page size 20 sorted by name) and `GET /api/pets` (owner sees only own pets even with a foreign `ownerId`; receptionist needs `ownerId`; veterinarian 403). Duplicate phone gives 409 `PHONE_TAKEN`, breed/species mismatch 400 `BREED_SPECIES_MISMATCH`, blank name/phone 400 with field errors.
- Frontend: `/reception/owners` (search, paginated list, register form with optional first pet), `/reception/owners/[id]` (pets table, add-pet form), `/owner/pets` (own pets, empty state) behind a new `owner/layout.tsx` role guard. Shared `PetFields` (breed select disabled until a species is chosen and filtered by species), `lib/breeds.ts`, new types, server actions in `app/actions/owners.ts` mapping backend errors to fields. Header links "Owners" and "My pets"; `Pagination` gained an optional `query` prop to keep the search term across pages.

### Decisions and deviations
- Completion criteria: `./mvnw verify`, `npm run lint` and `npm run build` pass. Browser manual check not performed; instead the live backend was exercised with curl (register Maria with dog, duplicate phone 409, search, mismatch 400, owner sign-in with phone sees only own pets, owner on `/api/owners` 403). Plan file is guarded, so criteria are recorded here and status set via script.
- `PetForm.tsx` exports `PetFields` (fields only, no `<form>`) so it embeds in both the owner and add-pet forms. Files added beyond the plan: `reception/owners/[id]/add-pet-form.tsx`; `Pagination.tsx` modified.
- Review findings: none target I6 (3 open minors elsewhere).

### Tests
- Backend: 288 run, 0 failed, 0 skipped (new: OwnerControllerTests 8, PetControllerTests 3). Frontend: lint and build pass; no frontend test suite.

### Blockers
- None

## 2026-10-19 — Increment 7: Availability, walk-in booking and appointment detail
- Status: completed

### Behavior delivered
- Backend: `GET /api/availability` (owner and receptionist; `serviceId` required, optional `veterinarianId`, `date` defaults to today) and `POST /api/appointments` (201 + `Location`; reception walk-in is `SCHEDULED` with booker `RECEPTIONIST`), `GET /api/appointments` (paginated, default size 20, scoped by role) and `GET /api/appointments/{id}` with `allowedActions`. Overlapping bookings give 409 `SLOT_TAKEN`; two concurrent HTTP bookings of one slot yield exactly one 201.
- Frontend: `/reception/book` (owner search, pet select, `SlotPicker` with service, vet and date selectors and loading/empty/error states, Book button; a taken slot shows "That slot was just taken" and reloads the slots) and `/appointments/[id]` (pet, owner, service, vet, time, duration, total, booker, observations, `StatusBadge`, `AppointmentActions`). Shared `StatusBadge`, `SlotPicker`, `AppointmentActions` (renders only actions with a wired handler, none yet), server actions `fetchSlots` and `bookAppointment`, new appointment types, "Book walk-in" header link.

### Decisions and deviations
- Completion criteria: `./mvnw verify`, `npm run lint` and `npm run build` pass (met). The browser manual check (two-tab slot race) was not performed in this session; the same behavior is covered by `WalkInBookingIntegrationTests` (real sign-in, slot removed from availability, concurrent POSTs give 201 + 409). Plan file is guarded, so criteria are recorded here and status set via script.
- The real `AppointmentStatus` enum is RESERVED, SCHEDULED, IN_PROGRESS, FINISHED, PAID, CLOSED, RESCHEDULED, CANCELLED (the plan text says "eight statuses"); `StatusBadge` mirrors the enum.
- `AppointmentController` lists with no filter (filters arrive in I10, I11, I13). Owner search and pet loading on `/reception/book` happen server-side through URL params (`q`, `ownerId`) instead of extra client actions.
- Review finding m1 (pet history endpoint mapping) targets I14 and I7's controller; not applicable now because the history endpoint is not built here. For I14, use a separate controller or method-level full paths. Other open minors do not target I7.

### Tests
- Backend: 300 run, 0 failed, 0 skipped (new: AvailabilityControllerTests 4, AppointmentControllerTests 6, WalkInBookingIntegrationTests 2). Frontend: lint and build pass; no frontend test suite.

### Blockers
- None

## 2026-10-19 — Increment 8: Online booking: reserve, confirm and reservation expiry
- Status: completed

### Behavior delivered
- Backend: `POST /api/appointments/{id}/confirm` (owner and receptionist; vet gets 403) returns the SCHEDULED appointment; expired holds give 409 `RESERVATION_EXPIRED`, repeated confirms 409 `INVALID_TRANSITION`. Owner reservation (RESERVED, `reservedUntil`) and the sweeper cancelling lapsed holds with "Reservation expired" were verified end to end over HTTP.
- Frontend: `/owner/book` (own pet select, `SlotPicker`, Reserve, reservation panel with countdown and Confirm; expiry shows "Reservation expired, pick a slot again" and reloads slots; slot conflicts reload slots), Confirm button wired on the appointment detail page, owner "Book" header link.

### Decisions and deviations
- Completion criteria: `./mvnw verify`, `npm run lint` and `npm run build` pass (met). Browser manual check and the short-TTL expiry check were not performed in this session; expiry is covered by `OnlineBookingIntegrationTests` with a movable clock. Plan file is guarded, so criteria are recorded here and status set via script.
- `bookAppointment` gained an optional `{ redirectOnSuccess: false }` so the owner form can show the reservation panel; `confirmFromDetail` is the form-action variant for the detail page.
- Review finding m3 (TTL env var name): not applicable to code; for the manual check use `APP_BOOKING_RESERVATIONTTL=PT1M` or `--app.booking.reservation-ttl=PT1M`.

### Tests
- Backend: 305 run, 0 failed, 0 skipped (new: 3 confirm cases in AppointmentControllerTests, OnlineBookingIntegrationTests 2). Frontend: lint and build pass; no frontend test suite.

### Blockers
- None
