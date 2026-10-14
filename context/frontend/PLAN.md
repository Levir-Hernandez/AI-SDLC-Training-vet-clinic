# Frontend Plan — Vet Clinic Service

## 1. Overview
Next.js 16 / React 19 / Tailwind 4 client for the Vet Clinic API, built on the existing skeleton. 12 ordered increments: foundation (client, types, shared UI, tests), sign-in and shell, owners, pets, catalog, appointment list and booking, appointment detail with status actions, clinical data, payments, audit log, search with cards and history, final polish. Each increment ships pages, components and Vitest/RTL/MSW tests and leaves `npm run lint`, `npm test` and `npm run build` green. Role and status gating is driven by `allowedActions` from the API plus the signed-in role.

## 2. Repository Findings
- `frontend/package.json`: next 16.4.0, react 19.3.0, tailwindcss 4 (`@tailwindcss/turbopack`), eslint 9, TypeScript 5. No test tooling yet; scripts `dev`, `build`, `start`, `lint` only (no `test`).
- `frontend/next.config.ts`: `cacheComponents: true`, `partialPrefetching: true`, Tailwind turbopack rule. Pages that read per-user data are client components (session cookie lives on the backend origin), so they stay dynamic.
- `frontend/lib/api.ts`: `API_BASE_URL` from `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`) and a `apiGet` without credentials or error parsing. Must be extended (credentials, JSON methods, `ProblemDetail` errors); `API_BASE_URL` and `apiGet` stay exported.
- `frontend/app/page.tsx`, `app/backend-status.tsx`, `app/layout.tsx`, `app/globals.css`: skeleton home that shows backend health. `page.tsx` is replaced by the Appointments page (Increment 6); `backend-status.tsx` is reused on the sign-in page.
- No `components/`, no types, no tests, no auth, no `.env.example`.
- Backend plan (`context/backend/PLAN.md`) is all `pending`; this plan consumes its `## API Contract`. Cookie auth requires `credentials: "include"`; backend CORS allows credentials for `http://localhost:3000`.

## 3. Architecture Decisions
```
frontend/
├── app/
│   ├── layout.tsx                    root layout, AuthProvider
│   ├── login/page.tsx                sign in (public)
│   ├── (app)/layout.tsx              guarded shell: nav, search box, user menu, sign out
│   ├── (app)/page.tsx                Appointments list (home)
│   ├── (app)/appointments/new/page.tsx
│   ├── (app)/appointments/[id]/page.tsx
│   ├── (app)/owners/page.tsx         list + create
│   ├── (app)/owners/[id]/page.tsx    owner card
│   ├── (app)/pets/[id]/page.tsx      pet card + history
│   ├── (app)/catalog/page.tsx        services and medications tabs (admin)
│   ├── (app)/audit-log/page.tsx      (admin)
│   └── (app)/search/page.tsx         global search results
├── components/
│   ├── ui/       StatusBadge, ConfirmDialog, ReasonDialog, EmptyState, Pagination, FormField,
│   │             Button, Spinner, ErrorMessage, Tabs, Modal
│   ├── auth/     AuthProvider, RequireAuth, RequireRole
│   ├── owners/   OwnerForm, OwnerTable, PetList
│   ├── pets/     PetForm, MedicalHistory
│   ├── catalog/  CatalogTable, ServiceForm, MedicationForm
│   ├── appointments/ AppointmentFilters, AppointmentTable, AppointmentForm, AppointmentHeader,
│   │                 ActionBar, ClinicalForm, LineItemsSection, PrescriptionsSection,
│   │                 PaymentsSection, StatusHistory
│   └── search/   GlobalSearchBox, PetPicker (searchable)
├── lib/
│   ├── api.ts          typed client: request(), apiGet/Post/Put/Delete, ApiError {status, code, detail, fieldErrors}
│   ├── types.ts        DTOs mirroring the API Contract
│   ├── status.ts       single status → label/colors mapping (UI-1)
│   ├── actions.ts      allowedActions → button definitions
│   ├── validation.ts   shared client validators mirroring backend constraints
│   ├── format.ts       money, date/time helpers
│   └── hooks/          useAuth, usePagedQuery, useDebounce
├── vitest.config.mts, vitest.setup.ts
├── tests/msw/          handlers.ts, server.ts, fixtures
└── .env.example        NEXT_PUBLIC_API_BASE_URL
```
Key decisions:
- **Auth guard:** client-side. `AuthProvider` calls `GET /api/auth/me` on load; `(app)/layout` renders `RequireAuth` and redirects to `/login` on 401. Any `401` from the client triggers a redirect to `/login`. Only `/login` renders without a session (AC-3).
- **Data fetching:** client components with small hooks (no new data library). Loading, empty, error states in every list via shared components.
- **Gating (UI-2/AC-23):** action buttons come from `AppointmentDetail.allowedActions` (state-machine output for the current user), combined with role checks for navigation (Catalog and Audit log only for ADMIN, "My appointments" only for VET). The backend still enforces everything.
- **Errors (UI-5):** `ApiError.fieldErrors` mapped to `FormField` messages; non-field errors shown by `ErrorMessage` using `detail`, with a code-to-text map for known `code`s (`PAST_DATE`, `DOUBLE_BOOKING`, `ILLEGAL_TRANSITION`, `REASON_REQUIRED`, `DIAGNOSIS_REQUIRED`, `NOT_EDITABLE`, `INACTIVE_CATALOG_ITEM`, `PAYMENT_NOT_ALLOWED`, `OVERPAYMENT`, `ROLLBACK_BLOCKED_BY_PAYMENTS`, `OWNER_HAS_OPEN_APPOINTMENTS`).
- **Status colors (UI-1):** one map in `lib/status.ts`, used by every badge (SCHEDULED slate, CHECKED_IN blue, IN_PROGRESS indigo, ON_HOLD amber, READY teal, PAID green, CLOSED gray, CANCELLED red).
- **Layout (UI-7):** tables inside responsive containers that reflow (stacked rows below `lg`), no horizontal scroll at 768 px and up.
- **Dates:** `scheduledAt` is an ISO instant; forms use `datetime-local` and convert to ISO; display in the browser's locale.
- **Tests:** Vitest + React Testing Library + MSW; API mocked at the network layer.

## 4. Assumptions
| # | Assumption |
|---|---|
| A1 | `allowedActions` values are defined by Backend Increment 7 and the API Contract: exactly `CHECK_IN`, `START_EXAM`, `PUT_ON_HOLD`, `RESUME`, `READY_FOR_DISCHARGE`, `CANCEL`, `CLOSE`, `ROLLBACK`, `RECORD_PAYMENT`, `EDIT_CLINICAL` (type `AllowedAction`). Unknown values are ignored. |
| A12 | Wire values follow the backend enums. `lib/types.ts` declares string unions: `Role = "ADMINISTRATOR" \| "VETERINARIAN"`, `Species = "DOG" \| "CAT" \| "OTHER"`, `Sex = "MALE" \| "FEMALE" \| "UNKNOWN"`, `PaymentMethod = "CASH" \| "CARD"`, `AuditType = "STATUS_CHANGE" \| "PAYMENT" \| "ROLLBACK"`, `AppointmentStatus` with the eight values. "ADMIN", "VET", "dog/cat", "cash/card" elsewhere in this plan are shorthand for display labels, which come from display maps; requests and role checks use the wire values. |
| A2 | Sign-in uses `POST /api/auth/login` with session cookie; every request sends `credentials: "include"`. Sign-out calls `POST /api/auth/logout` then redirects to `/login`. |
| A3 | A `401` on any call (except login) means the session ended: the client redirects to `/login`. |
| A4 | Veterinarians get read-only access to owners, pets, catalog (not shown in nav) and search; owner/pet/catalog/booking write buttons are hidden for them (A11 of backend). Catalog and Audit log nav entries are administrator-only. |
| A5 | Veterinarian dropdowns use `GET /api/employees?role=VETERINARIAN`. "My appointments" sets `veterinarianId` to `me.id` (backend A8). |
| A6 | Searchable pet picker and the appointments "pet" filter use `GET /api/pets?query=` (debounced, min 2 characters). |
| A7 | Soft-deleted owners in appointment detail (`deleted: true`) show a "Deleted" tag and no link (their card returns 404). A soft-deleted pet shows a "Deleted" tag and links to `/pets/{id}`; because `GET /api/pets/{id}` returns `404`, the pet card falls back to history-only (via `GET /api/pets/{id}/history`, which still works, backend Increment 11) with a "Deleted" tag. |
| A8 | The Owners page lists owners via `GET /api/owners?query=` with server pagination (page size 20). Catalog lists use paging too. |
| A9 | The line item form picks from active catalog items (`active=true`); inactive ones are not offered, and the `INACTIVE_CATALOG_ITEM` error is still handled. |
| A10 | Rolling back from `READY` with payments is shown as the backend error `ROLLBACK_BLOCKED_BY_PAYMENTS`; the Rollback button stays visible when `allowedActions` includes it. |
| A11 | Package additions are dev-only test tools (`vitest`, `@vitejs/plugin-react`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `msw`); no UI framework or data library is added. |

## 5. Implementation Increments

### Increment 1 — Foundation: tooling, API client, types, shared UI
- **Status:** `pending`
- **Goal:** Add test tooling, a typed API client with error parsing, DTO types and the reusable UI components used by every later screen.
- **Dependencies:** None
- **Acceptance criteria covered:** AC-2 (frontend talks to the backend via `NEXT_PUBLIC_API_BASE_URL`); UI-1 (single status map), UI-3/UI-4 (confirm and reason dialogs), UI-5 (field errors), UI-6 (empty states, pagination), UI-7 (responsive primitives)

#### API consumed
- `GET /api/health` (existing status component keeps working)

#### Files
**Create:**
- `frontend/lib/types.ts` — DTOs mirroring the API Contract (`Employee`, `OwnerSummary/Response`, `PetSummary/Response`, `ServiceItem`, `Medication`, `AppointmentSummary/Detail`, `LineItem`, `Prescription`, `Payment`, `AuditEntry`, `PageResponse<T>`, `ProblemDetail`)
  and the exact wire unions of A12 (`Role`, `Species`, `Sex`, `PaymentMethod`, `AuditType`, `AppointmentStatus`, `AllowedAction`)
- `frontend/lib/status.ts`, `frontend/lib/format.ts`, `frontend/lib/validation.ts` (plus display-label maps for roles, species, sex, payment method)
- `frontend/components/ui/*` — `StatusBadge`, `ConfirmDialog`, `ReasonDialog`, `EmptyState`, `Pagination`, `FormField`, `Button`, `Spinner`, `ErrorMessage`, `Modal`, `Tabs`
- `frontend/vitest.config.mts`, `frontend/vitest.setup.ts`, `frontend/tests/msw/server.ts`, `frontend/tests/msw/handlers.ts`
- `frontend/.env.example`
- Tests: `lib/api.test.ts`, `components/ui/*.test.tsx`
**Modify:**
- `frontend/lib/api.ts` — add `request`, `apiPost/Put/Delete`, `credentials: "include"`, `ApiError` parsing `ProblemDetail`
- `frontend/package.json` — `test` script and dev dependencies (A11)

#### Tasks
- `ConfirmDialog`: accessible modal (focus trap, Esc, labelled). `ReasonDialog`: confirm button disabled until the trimmed reason is non-empty.
- `FormField` shows a label, input and an `aria-describedby` error.
- `Pagination` and `EmptyState` take page/size/total and message props.

#### Tests
- `api.test.ts`: JSON success, `204`, `400` with `fieldErrors`, `409` with `code`, `401` mapped to `ApiError.status`, credentials included.
- `StatusBadge`: every status renders its label and its one mapped class; unknown status falls back.
- `ReasonDialog`: confirm disabled with empty and whitespace reason, enabled with text; Esc cancels.
- `ConfirmDialog`, `Pagination` (disabled first/last), `EmptyState`, `FormField` error text.

#### Completion criteria
- `cd frontend && npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 2 — Sign in, session guard and app shell
- **Status:** `pending`
- **Goal:** Sign-in page, session handling, guarded shell with role-aware navigation and sign out.
- **Dependencies:** Increment 1; Backend Increment 2
- **Acceptance criteria covered:** AC-3 (only sign-in is public in the UI), AC-4 (seeded accounts sign in and out), AC-2 (`npm run dev` talks to backend); UI-5 (login errors)

#### API consumed
- `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/health`

#### Files
**Create:**
- `frontend/app/login/page.tsx` — username/password form, backend status line, error on `401`
- `frontend/components/auth/AuthProvider.tsx`, `RequireAuth.tsx`, `RequireRole.tsx`; `frontend/lib/hooks/useAuth.ts`
- `frontend/app/(app)/layout.tsx` — shell: nav (Appointments, Owners; Catalog and Audit log for admin), user name and role, Sign out, responsive nav
- `frontend/components/ui/NavLink.tsx`
- Tests: `login.test.tsx`, `RequireAuth.test.tsx`, `shell.test.tsx`
**Modify:**
- `frontend/app/layout.tsx` — wrap with `AuthProvider`, metadata title
- `frontend/app/page.tsx` — moved to `app/(app)/page.tsx` as a placeholder until Increment 6

#### Tasks
- Client validation: both fields required. Show "Invalid username or password" for `401`.
- On login success redirect to `/`; `RequireAuth` shows spinner while `me` loads, redirects to `/login` on 401.
- `RequireRole` shows a "Not available for your role" empty state when a VET opens an admin URL.

#### Tests
- Login: blank fields blocked; wrong credentials show the message; success redirects.
- Unauthenticated visit to a guarded page redirects to `/login`; `/login` renders without session.
- Admin nav shows Catalog and Audit log; vet nav hides them; Sign out calls logout and redirects.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.
- Manual with backend: sign in as `admin` and `vet1`, sign out, protected pages redirect.

---

### Increment 3 — Owners list, create, edit and delete
- **Status:** `pending`
- **Goal:** Searchable paged owner list, create/edit form and owner card basics with soft delete.
- **Dependencies:** Increment 2; Backend Increment 3
- **Acceptance criteria covered:** AC-5 (create, edit, list, search, soft-delete owners; duplicate phone shown on the field), AC-7 (deleted owners vanish from list); UF-1 (step 1); rules: BR-1, BR-2, BR-6; UI-3, UI-5, UI-6

#### API consumed
- `GET /api/owners?query=&page=&size=`, `POST /api/owners`, `GET /api/owners/{id}`, `PUT /api/owners/{id}`, `DELETE /api/owners/{id}`

#### Files
**Create:**
- `frontend/app/(app)/owners/page.tsx` — search box (debounced), table, pagination, "New owner" (admin only)
- `frontend/app/(app)/owners/[id]/page.tsx` — owner details, Edit, Delete
- `frontend/components/owners/OwnerForm.tsx`, `OwnerTable.tsx`
- `frontend/lib/hooks/usePagedQuery.ts`, `useDebounce.ts`
- Tests: `OwnerForm.test.tsx`, `owners-page.test.tsx`, `owner-card.test.tsx`

#### Tasks
- Validation mirrors backend: full name and phone required; email optional but valid format.
- `409` on phone shows under the phone field. Delete uses `ConfirmDialog`; `OWNER_HAS_OPEN_APPOINTMENTS` shown as a readable message and the owner stays.
- Vets see the list and card read-only (no create/edit/delete).

#### Tests
- Form: blank name, blank phone, bad email blocked; email omitted accepted; backend `409` shows next to phone.
- List: loading, empty ("No owners found"), error with retry, pagination, search triggers query.
- Delete: confirm required; cancel does nothing; success removes row; `409 OWNER_HAS_OPEN_APPOINTMENTS` message shown.
- Vet view: no write buttons.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass (the `OWNER_HAS_OPEN_APPOINTMENTS` path is covered by an MSW mock).
- Manual check of the real `OWNER_HAS_OPEN_APPOINTMENTS` error after Backend Increment 6 is implemented.

---

### Increment 4 — Pets on the owner card
- **Status:** `pending`
- **Goal:** List, add, edit and soft-delete pets from the owner card and a basic pet card.
- **Dependencies:** Increment 3; Backend Increment 4
- **Acceptance criteria covered:** AC-6 (add/edit pets; duplicate chip and future birth date rejected), AC-7 (deleted pets hidden); UF-1 (step 2); rules: BR-3, BR-4, BR-5, BR-6; UI-3, UI-5

#### API consumed
- `GET /api/owners/{ownerId}/pets`, `POST /api/owners/{ownerId}/pets`, `GET /api/pets/{id}`, `PUT /api/pets/{id}`, `DELETE /api/pets/{id}`

#### Files
**Create:**
- `frontend/components/pets/PetForm.tsx`, `frontend/components/owners/PetList.tsx`
- `frontend/app/(app)/pets/[id]/page.tsx` — pet profile, Edit, Delete (history section added in Increment 11)
- Tests: `PetForm.test.tsx`, `PetList.test.tsx`, `pet-card.test.tsx`
**Modify:**
- `frontend/app/(app)/owners/[id]/page.tsx` — pets section with "Add pet"

#### Tasks
- Fields: name*, species* (sent as `DOG|CAT|OTHER`), breed, sex* (sent as `MALE|FEMALE|UNKNOWN`), date of birth (max today), weight (> 0), coloring, chip number.
- `409` chip error under chip field; delete pet via `ConfirmDialog`, handle open-appointment `409`.

#### Tests
- Missing name/species/sex blocked; birth date tomorrow blocked, today accepted; weight 0 and -1 blocked, empty accepted.
- Backend chip `409` shown on chip field.
- Create-pet request body carries uppercase `species` and `sex` (e.g. `DOG`, `MALE`).
- Pet list empty state; delete confirm and removal; vet read-only.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 5 — Catalog (services and medications)
- **Status:** `pending`
- **Goal:** Administrator catalog page with tabs, create, edit and activate/deactivate.
- **Dependencies:** Increment 2; Backend Increment 5
- **Acceptance criteria covered:** AC-9 (manage and deactivate catalog items); rules: BR-8, BR-9, BR-10; UI-5, UI-6; TC-4 (money shown with two decimals)

#### API consumed
- `GET/POST /api/services`, `GET/PUT /api/services/{id}`, `POST /api/services/{id}/activation`
- `GET/POST /api/medications`, `GET/PUT /api/medications/{id}`, `POST /api/medications/{id}/activation`

#### Files
**Create:**
- `frontend/app/(app)/catalog/page.tsx` — Services/Medications tabs, search, active filter, paging
- `frontend/components/catalog/CatalogTable.tsx`, `ServiceForm.tsx`, `MedicationForm.tsx`
- Tests: `ServiceForm.test.tsx`, `MedicationForm.test.tsx`, `catalog-page.test.tsx`
**Modify:**
- `frontend/lib/format.ts` — money formatter

#### Tasks
- Page wrapped in `RequireRole ADMIN`. Code/SKU editable only on create. Price >= 0, duration positive integer.
- Activate/deactivate toggle with inline "Inactive" badge; no delete action exists.

#### Tests
- Price -0.01 blocked, 0 accepted; missing fields blocked; duplicate code `409` on the code field.
- Toggle calls activation endpoint and updates the row; no delete button.
- Vet visiting `/catalog` sees the not-available state.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 6 — Appointments list and booking form
- **Status:** `pending`
- **Goal:** Home page listing appointments with filters and "My appointments", plus the booking form.
- **Dependencies:** Increment 2; Backend Increments 2, 4, 6
- **Acceptance criteria covered:** AC-10 (book; past date and double booking errors shown), AC-11 (list and filters by date range, status, vet, pet); UF-2; rules: BR-11, BR-12; UI-1, UI-5, UI-6, UI-7

#### API consumed
- `GET /api/appointments?from=&to=&status=&veterinarianId=&petId=&page=&size=`, `POST /api/appointments`, `GET /api/employees?role=VETERINARIAN`, `GET /api/pets?query=`

#### Files
**Create:**
- `frontend/app/(app)/appointments/new/page.tsx` (admin only)
- `frontend/components/appointments/AppointmentFilters.tsx`, `AppointmentTable.tsx`, `AppointmentForm.tsx`
- `frontend/components/search/PetPicker.tsx` — debounced searchable select
- Tests: `AppointmentFilters.test.tsx`, `appointments-page.test.tsx`, `AppointmentForm.test.tsx`
**Modify:**
- `frontend/app/(app)/page.tsx` — Appointments page with filters, table, pagination, "New appointment" (admin only), "My appointments" toggle (vet only)

#### Tasks
- Filters kept in URL query params so lists are shareable and survive refresh. Date range converted to ISO `from`/`to`.
- Columns: date/time, pet, owner, vet, `StatusBadge`, total; rows link to detail. Deleted pet/owner tagged.
- Form: pet (required), vet (required), date/time (required, not in the past, validated client-side), complaint. `409 PAST_DATE` and `DOUBLE_BOOKING` shown under the date/time field.

#### Tests
- Each filter alone and combined builds the expected request; "My appointments" sends own `veterinarianId` and is absent for admin.
- Empty state, error with retry, pagination.
- Form: missing pet/vet/date blocked; past date blocked; backend `DOUBLE_BOOKING` shown on field; success redirects to the new detail page.
- Vet has no "New appointment" button and `/appointments/new` shows not-available.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 7 — Appointment detail and status actions
- **Status:** `pending`
- **Goal:** Detail page with header, badge, allowed action buttons, cancel/rollback dialogs and status history.
- **Dependencies:** Increment 6; Backend Increments 7, 10
- **Acceptance criteria covered:** AC-12 (forward transitions available for the right role; rejected ones shown clearly), AC-13 (cancel with reason), AC-14 (hold/resume), AC-18 (close; terminal states have no actions), AC-19 (admin rollback with reason), AC-20 (history visible), AC-23 (only allowed actions, confirmations); UF-3, UF-4, UF-6, UF-7; rules: BR-18, BR-20, BR-22, BR-23; UI-2, UI-3, UI-4

#### API consumed
- `GET /api/appointments/{id}`, `POST /api/appointments/{id}/transitions`, `POST /api/appointments/{id}/rollback`, `GET /api/appointments/{id}/audit-entries`

#### Files
**Create:**
- `frontend/app/(app)/appointments/[id]/page.tsx`
- `frontend/components/appointments/AppointmentHeader.tsx`, `ActionBar.tsx`, `StatusHistory.tsx`
- `frontend/lib/actions.ts` — map `allowedActions` to `{label, target|rollback, confirm?, reason?}`
- Tests: `actions.test.ts`, `ActionBar.test.tsx`, `appointment-detail.test.tsx`

#### Tasks
- Header: pet (link; a deleted pet is tagged "Deleted" and still links to its history-only card, A7), owner (link, none if deleted), vet, scheduled time, status badge, cancellation reason when cancelled.
- Buttons: Check in, Start exam, Put on hold, Resume, Ready for discharge, Cancel (reason dialog), Close (confirm), Rollback (reason dialog, label shows previous step if known). Rendered only for actions in `allowedActions` (A1).
- After success replace the detail with the returned `AppointmentDetail`; show backend errors (`DIAGNOSIS_REQUIRED`, `ILLEGAL_TRANSITION`, `ROLLBACK_BLOCKED_BY_PAYMENTS`) in an alert. Disable buttons while a request is in flight.
- Status history table: time, user, from → to or amount, reason; empty state.

#### Tests
- `actions.test.ts`: every allowed action maps to the right request; unknown ignored.
- Per status x role matrix (admin, assigned vet, other vet) renders only the expected buttons; CLOSED and CANCELLED render none.
- Cancel and rollback: confirm disabled until reason; request body carries reason; Close asks confirmation.
- `DIAGNOSIS_REQUIRED` error displayed on Ready for discharge; `404` shows not-found state.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.
- Manual with backend: walk SCHEDULED to READY as admin and vet.

---

### Increment 8 — Clinical data, line items and prescriptions
- **Status:** `pending`
- **Goal:** Let the assigned vet edit clinical fields, line items and prescriptions while `IN_PROGRESS`/`ON_HOLD`, with a running total.
- **Dependencies:** Increment 7; Backend Increment 8
- **Acceptance criteria covered:** AC-15 (clinical edits only on own appointment and in `IN_PROGRESS`/`ON_HOLD`), AC-16 (total equals sum of line items, price shown as copied), AC-9 (inactive items not offered), AC-18 (read-only in terminal states); UF-3; rules: BR-13, BR-14, BR-15, BR-9, BR-10; UI-2, UI-5

#### API consumed
- `PUT /api/appointments/{id}/clinical`, `POST/DELETE /api/appointments/{id}/line-items[/{itemId}]`, `POST/PUT/DELETE /api/appointments/{id}/prescriptions[/{rxId}]`, `GET /api/services?active=true`, `GET /api/medications?active=true`

#### Files
**Create:**
- `frontend/components/appointments/ClinicalForm.tsx`, `LineItemsSection.tsx`, `PrescriptionsSection.tsx`
- Tests: `ClinicalForm.test.tsx`, `LineItemsSection.test.tsx`, `PrescriptionsSection.test.tsx`
**Modify:**
- `frontend/app/(app)/appointments/[id]/page.tsx` — mount sections; editable only when `allowedActions` has `EDIT_CLINICAL`, otherwise read-only text

#### Tasks
- Clinical form: anamnesis, diagnosis, notes with explicit Save; unsaved-change hint.
- Line items: add form (service or medication selector, quantity integer >= 1), table with name, quantity, unit price, line total, performed by, remove (with confirm); running total in the footer from `total`.
- Prescriptions: add/edit/delete; required medication name, dosage, frequency, duration; instructions optional.
- Handle `NOT_EDITABLE` and `INACTIVE_CATALOG_ITEM` with readable messages.

#### Tests
- Editable only with `EDIT_CLINICAL`; admin, other vet and terminal statuses see read-only content, no add/remove buttons.
- Quantity 0, -1, 1.5 blocked; add updates rows and total; remove updates total; decimals (19.99 x 3 = 59.97) formatted.
- Prescription required fields blocked; edit and delete work.
- `NOT_EDITABLE` error displayed.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 9 — Payments
- **Status:** `pending`
- **Goal:** Payments section with balance and a payment form for administrators on `READY` appointments.
- **Dependencies:** Increment 8; Backend Increment 9
- **Acceptance criteria covered:** AC-17 (payments only in READY, not above balance, becomes PAID at zero), AC-18 (close after PAID), AC-20 (payment appears in history); UF-5; rules: BR-16, BR-17, BR-21; UI-2, UI-5

#### API consumed
- `POST /api/appointments/{id}/payments`, `GET /api/appointments/{id}/payments`, `GET /api/appointments/{id}`

#### Files
**Create:**
- `frontend/components/appointments/PaymentsSection.tsx` — totals (total, paid, balance), payments table, payment form
- Tests: `PaymentsSection.test.tsx`
**Modify:**
- `frontend/app/(app)/appointments/[id]/page.tsx` — mount section, refetch detail and history after payment

#### Tasks
- Form (amount, method sent as `CASH|CARD`) shown only when `allowedActions` has `RECORD_PAYMENT`. Client validation: amount > 0 and <= balance; "Pay full balance" shortcut.
- After a payment refresh detail: status badge turns `PAID` and the Close button appears when the balance is zero.
- `OVERPAYMENT` and `PAYMENT_NOT_ALLOWED` errors shown under the amount field / as alert.

#### Tests
- Amount 0, negative and above balance blocked; backend `OVERPAYMENT` shown on field.
- Payment request body carries uppercase `method` (`CASH` or `CARD`).
- Partial payment keeps the form and updates balance; final payment hides the form and shows `PAID` plus Close.
- Form hidden for vets and for non-READY statuses; payments list and empty state.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 10 — Audit log page
- **Status:** `pending`
- **Goal:** Administrator audit log with filters and paging.
- **Dependencies:** Increment 2; Backend Increment 10
- **Acceptance criteria covered:** AC-20 (user, time, old/new values and reason visible); rules: BR-24, BR-25 (no edit/delete controls); UI-6

#### API consumed
- `GET /api/audit-entries?appointmentId=&actorId=&type=&from=&to=&page=&size=&sort=occurredAt,desc`, `GET /api/employees`

#### Files
**Create:**
- `frontend/app/(app)/audit-log/page.tsx`
- `frontend/components/audit/AuditFilters.tsx`, `AuditTable.tsx`
- Tests: `audit-log.test.tsx`

#### Tasks
- `RequireRole ADMIN`. Filters: user, type (status change, payment, rollback), date range, appointment id (also from `?appointmentId=` link). Newest first.
- Appointment ids link to detail. Read-only table; empty and error states.

#### Tests
- Each filter builds the right query; paging; empty state; vet is shown not-available; no write controls rendered.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 11 — Global search, owner timeline and pet medical history
- **Status:** `pending`
- **Goal:** Header search box with results page, owner card appointment timeline and pet card medical history.
- **Dependencies:** Increments 4, 7; Backend Increment 11
- **Acceptance criteria covered:** AC-22 (search by owner name, phone, pet name, chip), AC-21 (history chronological), AC-7 (deleted hidden from search, history viewable); UF-8; rules: BR-6; UI-6

#### API consumed
- `GET /api/search?q=&limit=`, `GET /api/pets/{id}/history`, `GET /api/owners/{id}/appointments`

#### Files
**Create:**
- `frontend/components/search/GlobalSearchBox.tsx`
- `frontend/app/(app)/search/page.tsx` — owners and pets groups linking to cards
- `frontend/components/pets/MedicalHistory.tsx`, `frontend/components/owners/OwnerTimeline.tsx`
- Tests: `GlobalSearchBox.test.tsx`, `search-page.test.tsx`, `MedicalHistory.test.tsx`
**Modify:**
- `frontend/app/(app)/layout.tsx` — mount `GlobalSearchBox`
- `frontend/app/(app)/pets/[id]/page.tsx`, `owners/[id]/page.tsx` — add history and timeline

#### Tasks
- Search sends only when `q` has at least 2 characters (matches backend `400`); debounced, submit goes to `/search?q=`.
- History in ascending date order: date, vet, status badge, complaint, diagnosis, prescriptions, line items; empty state; each entry links to the appointment.
- Pet card (`pets/[id]/page.tsx`): when `GET /api/pets/{id}` returns `404`, fall back to `GET /api/pets/{id}/history` and render history only, with a "Deleted" tag and no Edit/Delete; if history is also `404`, show the not-found state.

#### Tests
- One-character query does not call the API; results grouped; no results empty state.
- History renders in order given out-of-order data sorted ascending; includes closed/cancelled; empty state.
- Timeline lists appointments with badges.
- Pet card with pet `404` and history `200` shows history with the "Deleted" tag and no write buttons.

#### Completion criteria
- `npm test`, `npm run lint`, `npm run build` pass.

---

### Increment 12 — Polish, accessibility and end-to-end verification
- **Status:** `pending`
- **Goal:** Close cross-cutting gaps: responsive layout, a11y, consistent errors and a full-flow test.
- **Dependencies:** Increments 1-11
- **Acceptance criteria covered:** AC-2 (`npm run dev` works against the backend), AC-3 (every guarded page redirects), AC-23 (action gating sweep), UI-1 to UI-7 sweep; TC (stack: Next.js 16, React 19, TypeScript, Tailwind)

#### API consumed
- All operations used by Increments 2-11 (no new ones)

#### Files
**Create:**
- `frontend/tests/guarded-routes.test.tsx` — every route redirects to `/login` without session
- `frontend/tests/visit-flow.test.tsx` — MSW stateful flow: book, check in, start, edit, ready, pay, close
- `frontend/tests/status-colors.test.tsx` — all pages use the shared badge
**Modify:**
- `frontend/app/globals.css`, shared components — responsive tweaks at tablet width; focus styles
- `README.md` — only if frontend run commands changed (default: none)

#### Tasks
- Check tables and forms at 768 px: no horizontal scroll (container classes, stacked rows).
- Keyboard and label audit for dialogs, forms, nav; `aria-live` for alerts.
- Grep that no component calls `fetch` outside `lib/api.ts`.

#### Tests
- Guarded-route sweep; role-gated routes (`/catalog`, `/audit-log`, `/appointments/new`) for vet.
- Full visit flow with admin and vet sessions switching; terminal appointment shows no actions or edit controls.

#### Completion criteria
- `cd frontend && npm test`, `npm run lint`, `npm run build` pass.
- With backend running: `npm run dev`, sign in, complete UF-1 to UF-8 manually.

## 6. Coverage Matrix
| Code | Increment |
|---|---|
| UF-1 | 3, 4 |
| UF-2 | 6 |
| UF-3 | 7, 8 |
| UF-4 | 7 |
| UF-5 | 9 |
| UF-6 | 7 |
| UF-7 | 7 |
| UF-8 | 11 |
| UI-1 | 1, 6, 12 |
| UI-2 | 7, 8, 9 |
| UI-3 | 1, 3, 4, 7 |
| UI-4 | 1, 7 |
| UI-5 | 1, 2, 3, 4, 5, 6, 8, 9 |
| UI-6 | 1, 3, 5, 6, 10, 11 |
| UI-7 | 1, 6, 12 |
| BR-1, BR-2 | 3 |
| BR-3, BR-4, BR-5 | 4 |
| BR-6 | 3, 4, 11 |
| BR-7 | 3 (error display) |
| BR-8, BR-9, BR-10 | 5, 8 |
| BR-11, BR-12 | 6 |
| BR-13, BR-14, BR-15 | 8 |
| BR-16, BR-17, BR-21 | 9 |
| BR-18, BR-20, BR-22, BR-23 | 7 |
| BR-24, BR-25 | 7, 10 |
| BR-19 | Backend only (UI uses `allowedActions`) |
| TC (stack, UI-relevant) | 1, 12 |
| AC-2 | 1, 2, 12 |
| AC-3 | 2, 12 |
| AC-4 | 2 |
| AC-5 | 3 |
| AC-6 | 4 |
| AC-7 | 3, 4, 11 |
| AC-8 | 3 |
| AC-9 | 5, 8 |
| AC-10 | 6 |
| AC-11 | 6 |
| AC-12 | 7 |
| AC-13 | 7 |
| AC-14 | 7 |
| AC-15 | 8 |
| AC-16 | 8 |
| AC-17 | 9 |
| AC-18 | 7, 8, 9 |
| AC-19 | 7 |
| AC-20 | 7, 9, 10 |
| AC-21 | 11 |
| AC-22 | 11 |
| AC-23 | 7, 8, 9, 12 |
| AC-1, AC-24 | Backend only (frontend tests are listed per increment) |
