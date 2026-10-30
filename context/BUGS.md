# Bug Review

- **Date:** 2026-10-28
- **QA pass:** 1
- **Commit reviewed:** `256ae95`
- **Open:** 0 (Critical 0, High 0, Medium 0, Low 0)

## Bugs

### BUG-001: Veterinarian and owner registration forms reset after a server-side rejection; schedule Day then shows Monday but saves the earlier day on /reception/veterinarians

- **Status:** Verified
- **Severity:** High

#### Environment
- **Branch / Commit:** `main` @ `256ae95` + uncommitted changes
- **OS / Runtime:** Windows 10 (10.0.19045), Node v24.15.0, java version "25.0.2" 2026-01-20 LTS
- **Config / Flags:** backend http://localhost:8080, frontend http://localhost:3000, seed data from `DemoDataSeeder`

#### Steps to Reproduce
1. Signed out, open http://localhost:3000/login and sign in as `reception` / `reception123`.
2. Open Veterinarians. In "Register a veterinarian" fill Name `QA P1 Vet Repro`, Specialization `X`, Cost per service `-5`, Username `qa.vet.repro`, Password `qavet123`.
3. Keep block 1 (Monday 09:00-13:00), click "Add block" and set block 2 Day to `Wednesday`.
4. Click "Register veterinarian". The backend rejects the cost ("must be greater than or equal to 0.00" under Cost per service).
5. Look at the form: Name, Specialization, Cost, Username and Password are now empty, and block 2 Day shows `Monday` again.
6. Refill Name `QA P1 Vet Repro`, Specialization `X`, Cost `10.00`, Username `qa.vet.repro`, Password `qavet123`, set block 2 to 14:00-15:00 (leave Day as displayed, `Monday`), click "Register veterinarian".
7. Read the new row in the Veterinarians table.

Same reset on Owners: fill Name empty, Phone `555-0303`, Password `qaowner3`, click "Register owner" → "must not be blank" on Name, and Phone and Password are cleared too.

#### Expected Behavior
TASK.md acceptance: "Reception can register an owner with pets and a veterinarian with a weekly schedule; invalid data (...) is rejected with a clear message." After a rejection the entered values stay so only the wrong field needs fixing, and the schedule that is saved is the schedule shown on screen (TASK.md: "Veterinarians have ... a weekly working schedule. Availability is calculated from that schedule").

#### Actual Behavior
After the server rejects the submission every text field is emptied and the Day selects visually reset to `Monday`, while the component state keeps the earlier choice. The vet is saved as `Mon 09:00-13:00, Wed 14:00-15:00` although the form showed Monday for block 2. In SC-04 the same effect saved `QA P1 Vet` as `Mon 09:00-12:00, Tue 13:00-17:00` instead of two Monday blocks, so availability for that vet is computed from a schedule reception never saw. The owner form likewise clears Name, Phone and Password after "This phone number is already registered." or a missing-name error.

#### Evidence
- `context/evidence/P1-BUG-001-5.png` (after rejection: fields empty, block 2 Day shows Monday)
- `context/evidence/P1-BUG-001-6.png` (saved row: `Mon 09:00-13:00, Wed 14:00-15:00`)
- `context/evidence/P1-BUG-001-vet-2.png` (overlap rejection clears the vet fields)
- `context/evidence/P1-BUG-001-2.png`, `context/evidence/P1-BUG-001-4.png` (owner form cleared after rejection)

### BUG-002: Registration forms show raw Bean Validation messages ("size must be between 6 and 2147483647") on /reception/owners and /reception/veterinarians

- **Status:** Verified
- **Severity:** Low

#### Environment
- **Branch / Commit:** `main` @ `256ae95` + uncommitted changes
- **OS / Runtime:** Windows 10 (10.0.19045), Node v24.15.0, java version "25.0.2" 2026-01-20 LTS
- **Config / Flags:** backend http://localhost:8080, frontend http://localhost:3000, seed data from `DemoDataSeeder`

#### Steps to Reproduce
1. Signed out, sign in at http://localhost:3000/login as `reception` / `reception123`.
2. Open Veterinarians, leave every field of "Register a veterinarian" empty, click "Register veterinarian".
3. Read the messages under each field.
4. Fill Name, Specialization, Cost, Username and Password `qavet123`; add a second Monday block 11:00-14:00 next to Monday 09:00-13:00; submit and read the alert.

#### Expected Behavior
TASK.md acceptance: invalid data "is rejected with a clear message." Messages should read like the ones the app already uses ("This phone number is already registered.", "Add at least one schedule block.").

#### Actual Behavior
Field messages are raw framework text: Name / Specialization / Username "must not be blank", Cost per service "must not be null" (or "must be greater than or equal to 0.00" for `-5`), Password "size must be between 6 and 2147483647". The overlap alert reads "Blocks on MONDAY 09:00-13:00 and 11:00-14:00 overlap" with the enum name in capitals. The owner form shows the same "must not be blank" and "size must be between 6 and 2147483647".

#### Evidence
- `context/evidence/P1-BUG-002-1.png`
- `context/evidence/P1-BUG-001-vet-2.png` (overlap alert with "MONDAY")

### BUG-003: Ongoing appointments list offers only Cancel for a Reserved appointment; Confirm missing on /reception/appointments

- **Status:** Verified
- **Severity:** Medium

#### Environment
- **Branch / Commit:** `main` @ `256ae95` + uncommitted changes
- **OS / Runtime:** Windows 10 (10.0.19045), Node v24.15.0, java version "25.0.2" 2026-01-20 LTS
- **Config / Flags:** backend http://localhost:8080, frontend http://localhost:3000, seed data from `DemoDataSeeder`

#### Steps to Reproduce
1. Signed out, sign in at http://localhost:3000/login as an owner (e.g. `555-0101` / `qaowner1`, registered by reception first).
2. Open "Book", pick a pet, Vaccination, Dr. Ana Torres, a future weekday, click a free slot and "Reserve"; do not click "Confirm". Sign out.
3. Sign in as `reception` / `reception123`; the "Ongoing appointments" page opens.
4. Find the row with status Reserved and read its "Appointment actions" group.
5. Click "Details" on the same row and read the actions there.

#### Expected Behavior
TASK.md lifecycle table: "Reserved → Scheduled | Owner, reception | Confirmed before the reservation expires", and acceptance: "Each status shows a consistent badge, and only allowed actions are offered." The list row should offer the same allowed actions as the detail page (Confirm and Cancel).

#### Actual Behavior
The Reserved row's "Appointment actions" group contains only "Cancel". The detail page `/appointments/<id>` for the same appointment offers "Confirm" and "Cancel", so reception must leave the list to confirm.

#### Evidence
- `context/evidence/P1-BUG-003-1.png` (list row for #15 with Cancel only)
- `context/evidence/P1-BUG-003-2.png` (detail #15 with Confirm and Cancel)
- `context/evidence/P1-BUG-003-3.png` (second reproduction, row for #21)
