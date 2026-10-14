# Plan Review — Backend

## Review Metadata
- **Date:** 2026-10-14
- **Plan reviewed:** `context/backend/PLAN.md`
- **Review pass:** 1
- **Verdict:** REVISE BEFORE IMPLEMENTATION

## Blockers
None.

## Major Findings
None.

## Minor Findings
### m2 - Increment 7 happy-path test needs a diagnosis before the clinical endpoint exists
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increment 7 › Tests
- **Problem:** The integration test walks `IN_PROGRESS` → `READY`, which needs a diagnosis (the `READY` guard). The endpoint that sets it arrives in Increment 8.
- **Evidence:** Increment 7 and Increment 8 ordering.
- **Suggested fix:** State that Increment 7 sets the diagnosis through the repository as a fixture. Then repeat the flow over HTTP in Increment 12.

### m3 - Files listed under the wrong group in Increment 10
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increment 10 › Files
- **Problem:** `V10__audit_indexes.sql` is a new file but sits under **Modify**. Increment 12 lists the README as "only if", which is vague.
- **Evidence:** Plan format (Files).
- **Suggested fix:** Move the migration to **Create**. Remove the conditional README entry, or state "no README change".

### m4 - Inconsistent single owner of status assignment
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › 3. Architecture Decisions, Increment 12 › Tasks
- **Problem:** Architecture says `AppointmentService.transition` is the only place that changes status. Increment 12 checks that nothing outside `AppointmentStateMachine` assigns status. Increment 6 also sets `SCHEDULED` on creation.
- **Evidence:** BR-19.
- **Suggested fix:** Define one rule, for example "status is assigned only by the machine-approved method on `Appointment`, plus the initial `SCHEDULED`". Align the Increment 12 grep check with it.

### m5 - Inconsistent error status for blank reasons
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increment 7 › API provided
- **Problem:** A blank cancel reason returns `409 REASON_REQUIRED`, but a blank rollback reason returns `400`. The UI (UI-5) needs a consistent field-error shape.
- **Evidence:** BR-20, BR-23.
- **Suggested fix:** Return `400` with `fieldErrors.reason` for both, or document why they differ.

### m6 - Assumptions A6 and A12 extend the stated rules
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › 4. Assumptions (A6, A12), Increment 6 › Tests
- **Problem:** A6 moves a zero-total appointment to `PAID` at the moment of `READY`, so the administrator never sees `READY`. A12 blocks deleting a pet with an open appointment, which BR-7 states only for owners. Increment 6 tests also allow "400/404" for a missing pet or vet, while the contract says 404.
- **Evidence:** BR-7, BR-21, the state-machine table (READY → PAID "incl. total 0").
- **Suggested fix:** Flag A6 and A12 for user confirmation. Fix the test expectation to 404 for a missing pet or vet.

### m7 - Define PetSummary and OwnerSummary content for pickers and tables
- **Status:** open
- **Source:** validate-integration
- **Location:** `context/backend/PLAN.md` › Backend Increment 4
- **Problem:** `PetSummary` and `OwnerSummary` fields are not listed, but the searchable pet picker and the search page show owner name, species and chip, and `GET /api/pets?query=` does not say what it matches.
- **Evidence:** Frontend A6 and Increment 6 (`PetPicker`), Backend Increment 11 (`PetSummary with ownerName`), Backend Increment 4 (`GET /api/pets?query=`).
- **Suggested fix:** In Increment 4 define `PetSummary = {id, name, species, chipNumber, ownerId, ownerName}`; `query` matches pet name or chip number (case-insensitive, partial); `OwnerSummary = {id, fullName, phone, email, petCount}`, in Increment 3 if the owner increment is revised too.

### m8 - Include role in the employee list and make the filter optional
- **Status:** open
- **Source:** validate-integration
- **Location:** `context/backend/PLAN.md` › Backend Increment 2
- **Problem:** `GET /api/employees?role=` returns `{id,fullName,specialization}` only. The audit log user filter (Frontend Increment 10) calls it without a role and must list administrators as well, and cannot tell roles apart.
- **Evidence:** Backend Increment 2 API provided versus Frontend Increment 10 (`GET /api/employees`).
- **Suggested fix:** Return `{id, fullName, role, specialization}` and state that `role` is optional (no value returns all active employees).

## Resolved in Previous Passes
None.
