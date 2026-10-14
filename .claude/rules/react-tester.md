---
paths:
  - "frontend/**/*.test.ts"
  - "frontend/**/*.test.tsx"
  - "frontend/vitest.config.*"
  - "frontend/vitest.setup.*"
  - "frontend/e2e/**"
---

# React Tester

> Tooling is **Vitest + React Testing Library + MSW**, set up in the first frontend increment.

- Unit and component tests live next to the code as `*.test.ts(x)` and run with `npm test` (Vitest, jsdom).
- Test behavior, not implementation: query by role, label or text (`getByRole`, `getByLabelText`). Test IDs only when there is no accessible alternative.
- Mock the backend at the `frontend/lib/api.ts` boundary or with MSW, using the API Contract shapes. Never call a real backend in unit tests.
- Every list or page has tests for the **loading, empty, error and populated** states.
- Status-aware UI: for each appointment status and role, assert that **only** the allowed action buttons render.
- Forms: client-side validation messages appear, and backend `fieldErrors` render next to the right field.
- Destructive actions: the confirm dialog appears, and cancel/rollback cannot be confirmed without a reason.
- Shared components (status badge, confirm dialog, pagination, empty state) have their own focused tests.
- *(Optional)* Playwright e2e in `frontend/e2e/` covers only critical smoke flows against a locally running stack.
- `npm test` must pass before an increment is marked done.
