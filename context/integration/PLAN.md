# Integration Plan — Vet Clinic Service

## 1. Overview
Makes the backend (Spring Boot, `:8080`), the frontend (Next.js, `:3000`) and PostgreSQL (compose, `:5432`) run and work together locally. 8 ordered increments: startup and connectivity, authentication and CORS, registry and catalog, booking, visit lifecycle, payments, audit/search/history, and a final full-stack run (dev servers and `--profile app` containers). Each increment is a cross-area check (browser, API, database) that starts once the cited Backend and Frontend increments are implemented. Contract gaps found between the two plans are listed as Suggested Integration Adjustments (section 7), not as increments.

## 2. Repository Findings
- `compose.yaml`: `postgres:17.6` (db/user/password `vetclinic`, port 5432, healthcheck, named volume) plus `backend` and `frontend` under profile `app`. Backend gets `SPRING_DOCKER_COMPOSE_ENABLED=false` and `SPRING_DATASOURCE_*`; its healthcheck runs `curl` against `/actuator/health`. Frontend depends on backend healthy. Already valid; no change planned.
- `backend/src/main/resources/application.yml`: Docker Compose support (`../compose.yaml`, `start-only`), Flyway on, `ddl-auto: validate`, port 8080, actuator exposes `health,info`, `app.cors.allowed-origins=http://localhost:3000`.
- `SecurityConfig.java`: CORS on `/api/**` for the configured origins, methods GET/POST/PUT/PATCH/DELETE/OPTIONS, all headers, but no `allowCredentials`. Backend Increment 2 must add it (the session cookie needs it).
- `frontend/lib/api.ts`: `API_BASE_URL` from `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`), no `credentials`. Frontend Increment 1 changes this.
- `backend/Dockerfile`: temurin 21 JRE image. It is not verified that `curl` exists in it, and the compose healthcheck depends on it (checked in Increment 8).
- `frontend/Dockerfile`: bakes `NEXT_PUBLIC_API_BASE_URL` at build time (`http://localhost:8080`, browser-side).
- `README.md` documents only the agents; it has no run instructions, ports, variables or seed accounts. Increment 1 adds them.
- Backend and frontend reviews (pass 1) are open with minors; frontend M1 (`allowedActions` vocabulary) is `needs context` and needs a backend change (SA1). Backend and frontend plans are all `pending`.
- No `context/integration/` files existed before this plan.

## 3. Architecture Decisions
```
scripts/smoke.mjs     Node 22 (no dependencies) end-to-end API smoke check; one named step per increment
README.md             Run section: prerequisites, commands, ports, variables, seed accounts
```
- **Contract source of truth:** the backend `## 6. API Contract` plus `/v3/api-docs`; the frontend `lib/types.ts` mirrors it.
- **Local run:** `docker compose up -d` (database only), `cd backend && ./mvnw spring-boot:run`, `cd frontend && npm run dev`. The backend also starts the compose database itself (AC-1); `docker compose up -d` first is the explicit variant.
- **Start order:** PostgreSQL healthy, then backend (`/api/health` 200), then frontend. The frontend login page shows the backend status line.
- **Browser and cookies:** browser at `http://localhost:3000` calls `http://localhost:8080` with `credentials: "include"`. Both use the host `localhost` (same site, so the `SameSite=Lax` `JSESSIONID` cookie is sent); `127.0.0.1` would be another CORS origin and is not supported by default.
- **Smoke script:** `scripts/smoke.mjs` signs in with the seeded accounts, keeps the cookie, and runs the steps for the increments implemented so far (selected with `--through <n>`). Browser-only checks are written as manual steps in the completion criteria.
- **Test isolation (TC-5):** automated tests use Testcontainers only; Increment 8 verifies `./mvnw verify` passes with the compose database stopped.

## 4. Assumptions
| # | Assumption |
|---|---|
| A1 | Backend and frontend increments are implemented in the order of their own plans. One cross-plan order constraint: Frontend Increment 7 needs Backend Increment 10 (`GET /api/appointments/{id}/audit-entries`), so Backend Increments 1-10 precede Frontend Increment 7. Backend Increment 10 depends on Backend Increment 9 (payments), so the payments API already exists in Increment 5, but the payment flow is verified only in Increment 6. |
| A2 | Seed accounts are `admin/admin123`, `vet1/vet123`, `vet2/vet123` (Backend A1); local-development defaults only, documented in the README, never real secrets. |
| A3 | Each integration increment starts only after every Backend/Frontend increment it cites is `done`. |
| A4 | The browser checks are performed manually; the repository adds no browser-automation dependency (Playwright/Cypress), since the frontend plan keeps dev dependencies to test tools. |
| A5 | Dates travel as ISO-8601 instants in UTC (`2026-11-03T09:30:00Z`), date of birth as `YYYY-MM-DD`, money as JSON numbers with two decimals (`59.97`), enums as the uppercase backend codes (SA6). |
| A6 | The `app` compose profile is a convenience (existing `Dockerfile`s); the primary run path is the three dev commands. |

## 5. Implementation Increments

### Increment 1 — Local startup and connectivity
- **Status:** `pending`
- **Goal:** Whole stack starts with the documented commands and the frontend reaches the backend.
- **Dependencies:** Backend Increment 1; Frontend Increment 1 (credentialed CORS from Backend Increment 2 is not required here; the browser status check moves to Increment 2)
- **Acceptance criteria covered:** AC-1 (backend starts, connects to compose PostgreSQL, applies migrations), AC-2 (`npm run dev` talks to backend); TC-1 (Flyway only), TC-5 (local DB from compose)

#### Files
**Create:**
- `scripts/smoke.mjs` — step "health": `GET /api/health` is 200 with `status`; `GET /v3/api-docs` is 200
**Modify:**
- `README.md` — new "Run locally" section: prerequisites (Docker, Java 21, Node 22), the three start commands, URLs (`:3000`, `:8080`, `/swagger-ui.html`), variables from section 6, seed accounts, smoke command

#### Tasks
- Document start order and the readiness check (`GET /api/health`).
- Confirm `frontend/.env.example` (Frontend Increment 1) lists `NEXT_PUBLIC_API_BASE_URL` with the same default as section 6.

#### Tests
- `node scripts/smoke.mjs --through 1`: health and api-docs pass.
- Negative: backend stopped, the frontend page shows the backend status as unavailable without crashing.

#### Completion criteria
- `docker compose up -d`, then `cd backend && ./mvnw spring-boot:run`, then `cd frontend && npm run dev`.
- `http://localhost:8080/api/health` returns `200`; `http://localhost:8080/swagger-ui.html` loads; Flyway log shows all migrations applied.
- `http://localhost:3000` renders. The "backend reachable" status and the no-CORS-error check in the browser console are verified in Increment 2, because credentialed CORS (`allowCredentials(true)`) arrives in Backend Increment 2 and Frontend Increment 1 sends `credentials: "include"`.
- `node scripts/smoke.mjs --through 1` exits 0.

---

### Increment 2 — Sign-in, session cookie and CORS
- **Status:** `pending`
- **Goal:** Seeded users sign in and out from the browser across the two origins, and unauthenticated access is rejected on both sides.
- **Dependencies:** Increment 1; Backend Increment 2; Frontend Increment 2
- **Acceptance criteria covered:** AC-3 (only sign-in and health public), AC-4 (seeded accounts sign in and out); UF: all flows need it

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "auth": login admin, vet1, vet2 (200, `role`), `GET /api/auth/me`, logout 204, `/me` then 401, `GET /api/owners` without cookie 401, `/api/health` without cookie 200, CORS preflight from `http://localhost:3000` (`Access-Control-Allow-Credentials: true`, `PUT` and `DELETE` allowed), other origin rejected

#### Tasks
- Verify the login response shape (`id, fullName, role, specialization`) is what `AuthProvider` types expect, and that role values equal the frontend's role checks (SA6).
- Verify a `401` on `/api/auth/me` from the login page does not cause a redirect loop.

#### Tests
- `node scripts/smoke.mjs --through 2` passes.
- Manual: open `http://localhost:3000/` signed out, redirected to `/login`; sign in as `admin`, nav shows Catalog and Audit log; sign out; sign in as `vet1`, those entries are hidden; wrong password shows "Invalid username or password".

#### Completion criteria
- Start as in Increment 1; `node scripts/smoke.mjs --through 2` exits 0.
- Browser: `http://localhost:3000/login` shows the backend as reachable with no CORS error in the console (credentialed CORS now in place).
- Browser: `JSESSIONID` cookie is present on `localhost:8080` after sign-in, reload keeps the session, sign out returns to `/login`, and every guarded URL redirects to `/login` when signed out.

---

### Increment 3 — Registry flow and catalog (UF-1)
- **Status:** `pending`
- **Goal:** An administrator registers an owner and pets and manages the catalog; backend field errors appear next to the right form fields.
- **Dependencies:** Increment 2; Backend Increments 3, 4, 5; Frontend Increments 3, 4, 5
- **Acceptance criteria covered:** AC-5 (owners create/edit/list/search/soft-delete, duplicate phone rejected), AC-6 (pets, duplicate chip, future birth date rejected), AC-7 (deleted owner/pet hidden), AC-9 (catalog manage and deactivate); UF-1; TC-4 (money shown with two decimals)

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "registry": create owner, duplicate phone 409 with `fieldErrors.phone`, add pet, duplicate chip 409 with `fieldErrors.chipNumber`, future date of birth 400 with `fieldErrors.dateOfBirth`, soft-delete owner then absent from `GET /api/owners?query=`, create service with duplicate code 409 `fieldErrors.code`, deactivate

#### Tasks
- Check that `fieldErrors` keys equal the frontend form field names: `fullName, phone, email, name, species, sex, weight, dateOfBirth, chipNumber, code, sku, durationMinutes, price`.
- Check paging: the frontend `Pagination` and the backend `page` parameter agree on zero-based pages (SA2).

#### Tests
- `node scripts/smoke.mjs --through 3` passes.
- Manual: as admin create owner, create a second owner with the same phone (error under Phone); add pet, add second pet with the same chip (error under Chip number); delete owner and confirm it disappears from the list; create a service with price `0` and `-0.01` (second is rejected); as `vet1` write buttons are hidden.

#### Completion criteria
- Start as in Increment 1; smoke through 3 exits 0 and the manual steps above give the stated results.

---

### Increment 4 — Booking and appointment list (UF-2)
- **Status:** `pending`
- **Goal:** An administrator books an appointment from the browser; list filters and open-appointment guards work through the UI.
- **Dependencies:** Increment 3; Backend Increment 6; Frontend Increment 6
- **Acceptance criteria covered:** AC-8 (owner with open appointment cannot be deleted), AC-10 (book; past date and double booking rejected), AC-11 (list and filters), AC-7 (past appointments of deleted pet/owner viewable); UF-2

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "booking": book for vet1 (201, `SCHEDULED`), same vet and instant 409 `DOUBLE_BOOKING` with `fieldErrors.scheduledAt`, past instant 409 `PAST_DATE`, filters by `status`, `veterinarianId`, `petId`, `from/to`, owner delete 409 `OWNER_HAS_OPEN_APPOINTMENTS`

#### Tasks
- Check the `datetime-local` value converts to the ISO instant the backend accepts, and the displayed time round-trips.
- Check `AppointmentSummary` carries ids and deleted flags the table needs (SA3).

#### Tests
- `node scripts/smoke.mjs --through 4` passes.
- Manual: New appointment with a pet from the searchable picker, vet, future time; repeating it shows the error under the date/time field; as `vet1`, "My appointments" lists only own appointments and "New appointment" is absent; deleting the owner shows the open-appointment message.

#### Completion criteria
- Start as in Increment 1; smoke through 4 exits 0; the booked appointment shows a `SCHEDULED` badge in the list and detail.

---

### Increment 5 — Visit lifecycle and clinical data (UF-3, UF-4, UF-6, UF-7)
- **Status:** `pending`
- **Goal:** Appointment moves through the state machine from the UI with the right buttons per role and status, clinical data and line items are saved, cancel and rollback work with reasons.
- **Dependencies:** Increment 4; Backend Increments 7, 8, 9, 10 (the payments API from Backend 9 must exist because Backend 10 depends on it; its flow is verified in Increment 6); Frontend Increments 7, 8
- **Acceptance criteria covered:** AC-12 (forward transitions and rejection), AC-13 (cancel with reason), AC-14 (hold/resume), AC-15 (clinical edits only by assigned vet, in IN_PROGRESS/ON_HOLD), AC-16 (total equals line items, price snapshot), AC-18 (terminal states immutable), AC-19 (rollback admin only), AC-20 (status history), AC-23 (actions per status and role, confirmations); UF-3, UF-4, UF-6, UF-7

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "lifecycle": admin check in, vet1 start, vet2 start on vet1's appointment 403, hold, resume, `READY` without diagnosis 409 `DIAGNOSIS_REQUIRED`, clinical save, line items (service and medication, total `19.99 x 3 = 59.97`), inactive catalog item 409, `READY`, rollback by vet 403, rollback by admin with reason, cancel with and without reason, `allowedActions` equals the documented vocabulary for each role and status

#### Tasks
- Compare `allowedActions` values the backend returns with the frontend `lib/actions.ts` mapping, value by value, including `EDIT_CLINICAL` and `ROLLBACK` (SA1).
- Check error `code`s the frontend maps (`ILLEGAL_TRANSITION`, `REASON_REQUIRED`, `DIAGNOSIS_REQUIRED`, `NOT_EDITABLE`, `INACTIVE_CATALOG_ITEM`) equal the backend codes.

#### Tests
- `node scripts/smoke.mjs --through 5` passes.
- Manual: as admin check in; as `vet1` start exam, edit anamnesis/diagnosis/notes, add a service and a medication, put on hold (edit controls still available), resume, Ready for discharge; as `vet2` the same appointment shows no action buttons; as admin Rollback asks for a reason (confirm disabled until typed) and the status history lists every step; Cancel on a new `SCHEDULED` appointment requires a reason; `CANCELLED` shows no actions.

#### Completion criteria
- Start as in Increment 1; smoke through 5 exits 0; the browser walk reaches `READY` and the status history shows user, time, from/to and reason for each step.

---

### Increment 6 — Payments and closing (UF-5)
- **Status:** `pending`
- **Goal:** Payments up to the balance move the appointment to `PAID` and the administrator closes it.
- **Dependencies:** Increment 5; Backend Increment 9; Frontend Increment 9
- **Acceptance criteria covered:** AC-17 (payments only in READY, not above balance, PAID exactly at zero), AC-18 (close PAID; closed immutable), AC-20 (payments in history); UF-5; TC-4 (exact decimals end to end)

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "payments": payment on non-READY 409 `PAYMENT_NOT_ALLOWED`, partial payment keeps `READY`, overpayment by 0.01 409 `OVERPAYMENT`, rollback after partial payment 409 `ROLLBACK_BLOCKED_BY_PAYMENTS`, final payment makes `PAID`, vet payment 403, close, any change after close 409, zero-total appointment goes `READY` then `PAID`

#### Tasks
- Check that amounts remain exact: payment of `19.99` three times and `59.97` total show `balance 0.00` with no rounding in JSON or UI.
- Check the UI handles a `PAID` status returned directly from the `READY` transition (zero total).

#### Tests
- `node scripts/smoke.mjs --through 6` passes.
- Manual: as admin on a `READY` appointment, "Pay full balance" button; a partial payment keeps the form; the final payment shows `PAID` and a Close button; Close asks for confirmation; the closed appointment shows no actions and read-only sections.

#### Completion criteria
- Start as in Increment 1; smoke through 6 exits 0; the browser flow ends in a `CLOSED` appointment.

---

### Increment 7 — Audit log, global search and medical history (UF-8)
- **Status:** `pending`
- **Goal:** Administrators see the audit log; staff search and open owner and pet cards with timeline and history.
- **Dependencies:** Increment 6; Backend Increments 10, 11; Frontend Increments 10, 11
- **Acceptance criteria covered:** AC-20 (status changes, rollback and payments in the audit log with user, time, old/new, reason), AC-21 (pet history chronological), AC-22 (search by owner name, phone, pet name, chip), AC-7 (deleted hidden from search, history viewable); UF-8

#### Files
**Modify:**
- `scripts/smoke.mjs` — steps "audit-search": `GET /api/audit-entries` as admin (entries for Increments 5-6 flows, newest first) and 403 as vet, `GET /api/search?q=` by owner name, phone, pet name and chip, 400 for one character, pet history ascending, history of a soft-deleted pet by id, owner appointments timeline

#### Tasks
- Check search `q` minimum (2) matches between the header box and the backend; check `SearchResponse` and `HistoryEntry` fields match `lib/types.ts`.
- Check audit filter values `type` (`STATUS_CHANGE, PAYMENT, ROLLBACK`) and the user filter source (SA5).

#### Tests
- `node scripts/smoke.mjs --through 7` passes.
- Manual: header search by phone and by chip finds the owner and pet; a deleted owner is not found; the pet card of a pet with three visits lists them oldest first; Audit log as admin filters by user, type and appointment; as `vet1` `/audit-log` shows not available.

#### Completion criteria
- Start as in Increment 1; smoke through 7 exits 0 and the manual checks hold.

---

### Increment 8 — Full-stack run, containers and final verification
- **Status:** `pending`
- **Goal:** Prove the complete system from a clean start, in dev servers and in compose containers, with isolated tests.
- **Dependencies:** Increment 7; Backend Increment 12; Frontend Increment 12
- **Acceptance criteria covered:** AC-1, AC-2, AC-3 (sweep: signed-out API calls return 401 and guarded pages redirect), AC-24 (tests run on Testcontainers only); UF-1 to UF-8 (end-to-end pass); TC-1, TC-5

#### Files
**Modify:**
- `scripts/smoke.mjs` — step "sweep": every mapped path of `/v3/api-docs` called without cookie returns 401 except health, login, docs
- `README.md` — troubleshooting (ports in use, Docker not running, CORS origin override via `APP_CORS_ALLOWED_ORIGINS`), container run section
- `backend/Dockerfile` — only if `curl` is missing from the runtime image: add a healthcheck-compatible tool or change the compose healthcheck (decision recorded in PROGRESS)
- `compose.yaml` — only if the container healthcheck fails (same condition)

#### Tasks
- Clean start: `docker compose down -v`, then the three dev commands; UF-1 to UF-8 pass on the empty database (seed data only).
- Container run: `docker compose --profile app up --build`; the backend becomes healthy, the frontend at `:3000` signs in and completes one visit (Increments 3-6 flow).
- Run `cd backend && ./mvnw verify` with the compose database stopped (`docker compose down`): passes using Testcontainers only.
- Check `docker compose config` is valid.

#### Tests
- `node scripts/smoke.mjs` (all steps) passes against dev servers and against the containers.
- Manual: UF-1 to UF-8 in the browser as one session pair (admin and `vet1`).

#### Completion criteria
- `docker compose down -v && docker compose --profile app up --build`; open `http://localhost:3000`; sign in as `admin`; the full smoke script exits 0 and the browser flow ends with a closed appointment visible in the audit log.
- `cd backend && ./mvnw verify` passes with `docker compose down` executed beforehand; `cd frontend && npm test && npm run lint && npm run build` pass.

## 6. Shared Configuration

| Item | Value / default | Source |
|---|---|---|
| Frontend URL | `http://localhost:3000` | Frontend Dockerfile, compose port 3000, `app.cors.allowed-origins` |
| Backend URL | `http://localhost:8080` | `application.yml` `server.port`, compose |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` (build-time in the container image) | `frontend/lib/api.ts`, `.env.example` (Frontend Increment 1), Frontend Dockerfile |
| `app.cors.allowed-origins` / env `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000`; credentials allowed; methods GET, POST, PUT, PATCH, DELETE, OPTIONS | `application.yml`, Backend Increments 2 and 12 |
| Database | PostgreSQL `postgres:17.6`, host `localhost:5432`, db/user/password `vetclinic` (local only) | `compose.yaml` |
| Container backend env | `SPRING_DOCKER_COMPOSE_ENABLED=false`, `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/vetclinic`, `SPRING_DATASOURCE_USERNAME/PASSWORD=vetclinic` | `compose.yaml` (profile `app`) |
| Spring compose support (dev) | file `../compose.yaml`, `lifecycle-management: start-only` | `application.yml` |
| Health endpoints | `GET /api/health` (frontend status) and `/actuator/health` (container healthcheck); both public | Backend Increments 1, 2 |
| API docs | `/swagger-ui.html`, `/v3/api-docs`; public | Backend Increments 1, 2 |
| Authentication | `POST /api/auth/login` JSON, cookie `JSESSIONID` (`SameSite=Lax`), requests with `credentials: "include"`, CSRF disabled | Backend A2, Frontend A2 |
| Seed accounts | `admin/admin123` (ADMINISTRATOR), `vet1/vet123`, `vet2/vet123` (VETERINARIAN) | Backend A1 |
| Error body | `ProblemDetail {type,title,status,detail,code,fieldErrors?}` | Backend Increment 1, Frontend `ApiError` |
| Pagination | `page`, `size`, `sort`; response `{content,page,size,totalElements}`; zero-based page, default size 20 (to be confirmed, SA2) | Backend Increment 1, Frontend A8 |
| Formats | instants ISO-8601 UTC, date of birth `YYYY-MM-DD`, money JSON number with 2 decimals | Backend Architecture (Money, Time), Frontend A (Dates) |
| Enum wire values | roles `ADMINISTRATOR`/`VETERINARIAN`; species `DOG/CAT/OTHER`; sex `MALE/FEMALE/UNKNOWN`; payment `CASH/CARD`; audit type `STATUS_CHANGE/PAYMENT/ROLLBACK`; statuses as TASK.md | Backend A3, Increment 7, 9 |
| Tests | Testcontainers `postgres:17.6`; never the compose database | Backend Increment 12, TC-5 |
