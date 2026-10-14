# Plan Review — Integration

## Review Metadata
- **Date:** 2026-10-14
- **Plan reviewed:** `context/integration/PLAN.md` (against `TASK.md`, `context/backend/PLAN.md`, `context/frontend/PLAN.md`, their reviews, `compose.yaml`, `backend/Dockerfile`)
- **Review pass:** 1
- **Verdict:** READY WITH MINOR CHANGES

Checks: coverage of UF-1 to UF-8, AC-1 to AC-24 and TC-1, TC-4, TC-5 is complete (TC-2, TC-3, TC-6 are backend-only). Every cited Backend/Frontend increment exists. Startup is verified in Increments 1 and 8. Shared Configuration matches the backend and frontend defaults. SA1 to SA7 each name an existing `pending` increment, are small, and none duplicates an open finding in the backend or frontend review (frontend M1 only asks for the backend change that SA1 proposes).

## Blockers
None.

## Major Findings
None.

## Minor Findings
### m3 - Increment 8 leaves the container healthcheck decision open
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increment 8 › Files, 2. Repository Findings
- **Problem:** `backend/Dockerfile` and `compose.yaml` are listed as "only if" files, and the `curl` question is deferred to the end. `compose.yaml` runs `curl` in the backend healthcheck, while the Dockerfile installs nothing, so the frontend container may never start. This can be checked now.
- **Evidence:** `compose.yaml` backend healthcheck; `backend/Dockerfile` runtime stage.
- **Suggested fix:** Verify now whether the runtime image has `curl`. Then list the exact change (install `curl`, or use a Java-based healthcheck) as a definite file change in Increment 8, or state "no change" in the Files list.

### m4 - Covered lines and smoke steps miss some codes
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increments 4, 5
- **Problem:** Increment 4 lists AC-7 but no smoke step checks a past appointment of a deleted pet or owner (only Increment 7 does). Increment 5 tests inactive catalog items (AC-9) but its covered line omits AC-9.
- **Evidence:** TASK.md AC-7, AC-9.
- **Suggested fix:** Remove AC-7 from Increment 4 (it stays in Increment 7) and add AC-9 to Increment 5.

## Resolved in Previous Passes
None.
