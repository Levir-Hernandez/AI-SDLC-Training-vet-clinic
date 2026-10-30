# Changelog

## [Unreleased] — 2026-10-28

### Added
- Created an implementation plan with 14 ordered increments covering authentication, veterinarians and services, owners and pets, availability, walk-in and online booking, cancellation, visits, the reception board, rescheduling, payments and debts, pet history, badge consistency and a final acceptance pass.
- Defined a single lifecycle component that owns the full transition table and drives the allowed actions shown in the UI.
- Specified slot-level concurrency protection through a veterinarian row lock and an overlap re-check at booking.
- Recorded assumptions covering owner and veterinarian accounts, seed credentials and schedules, time zone and slot grid, debt definition, reassignment totals and the Secure cookie on localhost.
- Added a first plan review that flagged the increment ordering, an early use of the debt flag, an unbound status filter, the pet history endpoint mapping, a missing reschedule page guard and an unused owner history endpoint.
- Added a core domain increment that creates every entity, enum and repository together with the appointment lifecycle table and its tests.
- Added two application-service increments that build the registration use cases and all appointment and payment use cases in their final shape, tested with a fixed clock and a service-level concurrency test.
- Added an assumption that the first three increments are backend-only and change nothing visible.
- Added a second plan review that confirmed the layered increment order and flagged an undefined validation rule on the cancel request.
- Added a third plan review that confirmed the status filter binding, the reschedule page guard and the cancel validation fixes.
- Added an owner pet history page reached from a History link per pet on the owner's pets list, using the existing pet history endpoint and showing a not-found page for another owner's pet.
- Recorded an assumption that owners may view their own pets' service history, since the task lists only reception history.
- Added a fourth plan review that confirmed the owner pet history view and flagged an unsupported date default on the veterinarian list and a mislabelled allowed-actions test.
- Added a fifth plan review that confirmed the veterinarian list and allowed-actions test fixes, and flagged a weak pet history slice test and a reservation TTL override that may not take effect in the manual expiry check.

### Changed
- Reordered the plan so authentication, seed data, controllers and screens come as vertical slices after the domain and service layers, growing the plan from 14 to 16 increments.
- Changed the slice increments so they only add controllers, security wiring and screens on top of the existing services.
- Moved the full demo seed (receptionist, veterinarians and services) into the authentication increment.
- Listed every supported appointment list query parameter and the increment that adds it in the Backend API Summary.
- Included the owner pet history page in the badge and allowed-actions consistency pass.

### Fixed
- Replaced the framework validation messages on the owner, pet and veterinarian registration forms with clear per-field messages such as "Enter the owner's name." and "The password must have at least 6 characters", and the schedule error messages now name the day in normal case, e.g. "Blocks on Monday 09:00-13:00 and 11:00-14:00 overlap".
- Fixed the reception ongoing appointments list so a Reserved appointment offers Confirm alongside Cancel, matching its detail page.
- Fixed the veterinarian, owner and add-pet forms so a rejected submission keeps the entered values and every schedule Day select shows the day that is actually submitted.
- Fixed the piecemeal design of the booking and action services, including the late extraction of the shared scheduled-booking method used by rescheduling.
- Fixed the early use of the debt flag, which is now computed only after payments exist.
- Bound the comma-separated status filter on the appointment list in the visit increment, with tests for valid and unknown values.
- Added a server-side receptionist role check to the reschedule page.
- Declared the cancel request reason as required and at most 500 characters, and aligned the cancel dialog with the validation error the API returns.
- Reclassified the allowed-actions response test in the badge consistency increment as an integration test, so the real query service and lifecycle produce the actions it checks.
- Removed the unsupported today-and-upcoming default from the veterinarian appointment list, which now relies on the non-final status filter and start-time ordering.
