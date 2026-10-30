# QA Report

- **Date:** 2026-10-28
- **QA pass:** 1
- **Commit reviewed:** `main` @ `256ae95` + uncommitted changes
- **Acceptance:** 12/15 passing (2 failing, 0 blocked, 1 untested)

## Summary
Booking, availability, concurrency, the full lifecycle, payments, debts, history and owner data isolation all behave as TASK.md requires. AC-1 fails: after the server rejects a submission, the registration forms reset. On the veterinarian form the schedule Day selects then show Monday but still submit the day chosen earlier, so a vet can be saved with a schedule reception never saw (BUG-001, High). Validation text is raw framework wording (BUG-002, Low). AC-14 fails because the ongoing list leaves Confirm off Reserved rows (BUG-003, Medium). BUG-001 blocks release. Open question: TASK.md fixes the total "at booking", but a reassignment (#18 Dr. Luis Gomez 65.00 → #19 Dr. Ana Torres 55.00) recomputes it, as plan assumption A6 allows. Whether that is intended is a product decision.

## Acceptance Criteria
- [ ] **AC-1:** Reception can register an owner with pets and a veterinarian with a weekly schedule; invalid data is rejected with a clear message: **Failing**, see BUG-001 (and BUG-002)
- [x] **AC-2:** The breed picker only offers breeds of the selected species
- [x] **AC-3:** An owner sees only free slots for the chosen service, veterinarian and date, and the slots refresh when any of them change
- [x] **AC-4:** An owner can reserve and confirm a slot; an unconfirmed reservation expires and the slot becomes free again
- [x] **AC-5:** Two bookings for the same veterinarian and overlapping time cannot both succeed
- [x] **AC-6:** A walk-in booked by reception is scheduled immediately and records reception as the booker
- [x] **AC-7:** Each status transition in the table works for the listed roles, and every other transition is rejected
- [x] **AC-8:** Cancelling without a reason is rejected
- [x] **AC-9:** Rescheduling or reassigning marks the original as rescheduled and creates a linked scheduled appointment
- [x] **AC-10:** A payment is accepted only for a finished appointment and only for its exact total; a second payment is rejected
- [x] **AC-11:** A finished appointment closed without payment appears as a debt
- [x] **AC-12:** An owner sees all their appointments across pets and days with estimated duration and cannot see other owners' data
- [x] **AC-13:** Reception sees all ongoing appointments with cancel and payment actions, and can open a pet's service history
- [ ] **AC-14:** Each status shows a consistent badge, and only allowed actions are offered: **Failing**, see BUG-003
- [ ] **AC-15:** The backend builds and its tests pass; the frontend builds and lints cleanly: **Untested**, not a browser check; this pass did not run `mvnw verify`, `npm run lint` or `npm run build`

## Coverage
- **Tested:** SC-01 to SC-20. Owner, pet and vet registration and their rejections (missing name, duplicate phone, duplicate username, overlap, end before start, no blocks, negative cost). Breed picker by species. Slot list refresh on service, vet and date change; past date and non-working day. Reserve and confirm with total 60.00. Reservation expiry after the 10-minute TTL: #15 became Cancelled ("Reservation expired") and 10:00 was offered again (`P1-SC08.gif`). Stale-list concurrent walk-in rejected with "That slot was just taken. Pick another one." and a refreshed list (`P1-SC09.gif`). Walk-in Scheduled, booked by Reception, 55.00. Vet start, observations, finish; another vet gets a 404. Owner cancel, with Confirm disabled for an empty or whitespace reason. Reception cancel of Scheduled and In progress. Reschedule of Scheduled, reassign of In progress, both with links. Payment of 54.99 rejected, exact payment accepted, second payment from a stale tab rejected ("This appointment was already paid."), close. Close unpaid listed in Debts. Pet history including Cancelled, Rescheduled and Closed. Owner refused on another owner's pet, appointment, `/reception/*` and `/vet/*`. `document.cookie` and `localStorage` empty. Sign-out redirects to `/login`. Double-clicking Reserve and "Book walk-in" created no duplicates.
- **Not tested:** SC-12 step 5 (owner offered no Cancel on In progress). The only Owner One appointment that was In progress was moved straight to Finished. SC-17 step 4: no vet filter on the ongoing list. Badge colours were compared by label text only. Outside the scenarios: at 375 px `/owner/appointments` scrolls horizontally (scrollWidth 505), noted, not filed. The backend was not fresh at the start, so leftover "QA P1 Owner A/B/C" and "QA P1 ..." vets from earlier runs were present; IDs in the evidence follow that state. No flaky results.
- **Automated tests:** 0 passed, 0 failed, 0 skipped (not run in this pass)

## Bugs Found This Pass
- **BUG-001** (High): Veterinarian and owner registration forms reset after a server-side rejection; schedule Day then shows Monday but saves the earlier day on /reception/veterinarians, affects AC-1
- **BUG-002** (Low): Registration forms show raw Bean Validation messages ("size must be between 6 and 2147483647") on /reception/owners and /reception/veterinarians, affects AC-1
- **BUG-003** (Medium): Ongoing appointments list offers only Cancel for a Reserved appointment; Confirm missing on /reception/appointments, affects AC-14 (and AC-7 for reception from the list)

## Previously Reported Bugs
- None (first pass).
