# Plan Review — Frontend

## Review Metadata
- **Date:** 2026-10-14
- **Plan reviewed:** `context/frontend/PLAN.md` (against `TASK.md` and `context/backend/PLAN.md` § API Contract)
- **Review pass:** 1
- **Verdict:** READY WITH MINOR CHANGES

Checks: coverage is complete (UF-1..8, UI-1..7, UI-relevant TC, AC-2..23 appear in increment lines; BR-19, AC-1 and AC-24 are correctly backend-only). Ordering is backwards-only, increments are testable with concrete commands, and no out-of-scope or backend-internal content was found.

## Blockers
None.

## Major Findings
None.

## Minor Findings
### m3 - Undocumented query parameters
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increment 10 API consumed
- **Problem:** `sort=occurredAt,desc` and `page/size` are not in the contract rows for audit entries.
- **Evidence:** API Contract row `GET /api/audit-entries?...`.
- **Suggested fix:** Drop `sort` (backend orders newest first) or confirm it in the backend contract.

### m4 - Missing zero-total and rollback label cases
- **Status:** open
- **Source:** validate-regular-plan
- **Location:** PLAN.md › Increments 7, 9
- **Problem:** Backend A6 makes a zero-total appointment jump `READY` → `PAID` on "Ready for discharge", but no frontend test covers the returned `PAID` status and Close button. The rollback label "previous step if known" has no data source in the contract.
- **Evidence:** UF-5, BR-21; Backend A6.
- **Suggested fix:** Add a test in Increment 7 for a response with status `PAID`; derive the rollback label from a fixed chain in `lib/status.ts` or use a plain "Roll back" label.

### m5 - Request enough items for catalog dropdowns
- **Status:** open
- **Source:** validate-integration
- **Location:** `context/frontend/PLAN.md` › Frontend Increment 8
- **Problem:** The line item selectors call `GET /api/services?active=true` and `GET /api/medications?active=true`, which are paged; with the default size only the first page would be offered.
- **Evidence:** Frontend Increment 8 (API consumed), Backend Increment 5 (`PageResponse`), SA2 (default 20, max 100).
- **Suggested fix:** Send `size=100` for the two selector calls and keep the text search `query` for larger catalogs.

## Resolved in Previous Passes
None.
