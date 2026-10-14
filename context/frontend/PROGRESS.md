# Progress

## 2026-10-14 — frontend — Plan generated
- Status: plan generated
- Details: Created `context/frontend/PLAN.md` with 12 pending increments from TASK.md and the backend API Contract.

### Added
- Created a frontend implementation plan with 12 ordered increments covering tooling and shared UI, sign-in, owners, pets, catalog, appointment list and booking, appointment detail with status actions, clinical data, payments, audit log, search with medical history, and final polish.
- Recorded the assumed `allowedActions` vocabulary (A1) that the backend needs to confirm.

## 2026-10-14 — frontend — Validation
- Status: review verdict READY WITH MINOR CHANGES (pass 1)
- Details: Wrote `context/frontend/PLAN_REVIEW.md`. Open: M1 - allowedActions vocabulary not in the contract; m1 - Deleted pet history is unreachable; m2 - Backend dependencies incomplete; m3 - Undocumented query parameters; m4 - Missing zero-total and rollback label cases.

### Added
- Added the first frontend plan review with one major and four minor findings.

## 2026-10-14 — frontend — Revision: M1 - allowedActions vocabulary not in the contract
- Status: plan revised (no plan edits)
- Details: The fix requires a backend contract change (enumerating `allowedActions` values), which is outside the frontend area. `PLAN.md` left unchanged; M1 marked `needs context` in `PLAN_REVIEW.md`.

## 2026-10-14 — frontend — Revision: M1 - allowedActions vocabulary not in the contract
- Status: plan revised
- Details: Backend Increment 7 now lists the ten `allowedActions` values; A1 in `PLAN.md` updated to cite them. M1 marked resolved.

### Changed
- Marked the `allowedActions` vocabulary as defined by the backend plan instead of an unconfirmed assumption.

## 2026-10-14 — frontend — Revision: M2 - Use backend enum wire values in the types and role checks
- Status: plan revised
- Details: Added A12 and edits in Increments 1, 4 and 9 of `PLAN.md`; M2 marked resolved.

### Added
- Added a test requirement that create-pet and payment requests send uppercase enum values.

### Fixed
- Declared the exact backend wire values (`ADMINISTRATOR`, `VETERINARIAN`, `DOG`, `MALE`, `CASH`, audit types, statuses) in the shared types and noted that plan labels are display shorthand.

## 2026-10-14 — frontend — Revision: m1 - Deleted pet history is unreachable
- Status: plan revised
- Details: Edited A7 and Increments 7 and 11 of `PLAN.md`; m1 marked resolved.

### Added
- Added a pet card fallback that shows history only with a "Deleted" tag when the pet itself returns 404, with a test for it.

### Fixed
- Aligned the deleted-pet assumption so the appointment header links a deleted pet to its history.

## 2026-10-14 — frontend — Revision: m2 - Backend dependencies incomplete
- Status: plan revised
- Details: Edited Increments 3 and 6 of `PLAN.md`; m2 marked resolved.

### Fixed
- Set the appointments list increment to depend on Increment 2 and Backend Increments 2, 4 and 6.
- Noted that the open-appointment owner delete error is mocked in tests and verified manually after Backend Increment 6.
