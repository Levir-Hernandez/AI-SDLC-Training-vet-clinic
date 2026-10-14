# Backend Plan — Vet Clinic Service

## 1. Overview
Spring Boot 4.1 REST API on PostgreSQL 17 + Flyway, built on the existing skeleton. 12 ordered increments: shared error handling, authentication, owners, pets, catalog, appointment booking, state machine with audit, clinical data and line items, payments, audit log API, search and medical history, final hardening. Each increment ships migrations, service rules, controllers and tests (unit, MockMvc, Testcontainers) and keeps `./mvnw verify` green.

## 2. Repository Findings
- `backend/pom.xml`: Boot 4.1.1, webmvc, data-jpa, flyway, security, validation, actuator, springdoc 3.1.0, docker-compose support, Testcontainers and the starter test modules. No new dependencies needed.
- `application.yml`: docker compose file `../compose.yaml`, `ddl-auto: validate`, Flyway enabled, `app.cors.allowed-origins`. Reused unchanged except for session/timezone settings.
- `config/SecurityConfig.java`: permits everything, CSRF off, CORS for `/api/**` without credentials. Must be replaced (Increment 2) and CORS must allow credentials.
- `health/HealthController` (`GET /api/health`) and `HealthControllerTests` exist; stay public.
- `db/migration/V1__baseline.sql` is `SELECT 1;`; never edited, new migrations start at V2.
- Tests: `TestcontainersConfiguration` (package-private, `postgres:17.6`, `@ServiceConnection`), `VetClinicBackendApplicationTests`, `TestVetClinicBackendApplication`. Reused by all integration tests.
- `compose.yaml` already provides PostgreSQL 17.6 and the optional `app` profile; no change planned.
- No domain code, no error handling, no OpenAPI config yet.

## 3. Architecture Decisions
```
com.vetclinic
├── VetClinicBackendApplication
├── config/        SecurityConfig, ClockConfig, OpenApiConfig
├── shared/        BusinessRuleException, NotFoundException, IllegalTransitionException,
│                  GlobalExceptionHandler (@RestControllerAdvice, ProblemDetail), PageResponse<T>
├── security/      Employee login: AuthController, EmployeeUserDetailsService, CurrentUser helper
├── employee/      Employee entity (+ Role enum), EmployeeRepository, EmployeeController (vet list)
├── owner/         Owner entity, repository, service, controller, DTOs
├── pet/           Pet entity, repository, service, controller, DTOs
├── catalog/       ServiceItem + Medication entities, repositories, services, controllers, DTOs
├── appointment/   Appointment, LineItem, Prescription entities; AppointmentStatus,
│                  AppointmentStateMachine, AppointmentService, ClinicalService,
│                  controllers, DTOs
├── payment/       Payment entity, repository, PaymentService, controller, DTOs
├── audit/         AuditEntry (append-only), AuditService, AuditController
└── search/        SearchController, SearchService (owners + pets)
```
Key decisions:
- **Auth:** session cookie (`JSESSIONID`) with a JSON login `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`. Passwords BCrypt. CORS allows credentials for the configured origin. CSRF stays disabled (cookie is SameSite=Lax, API is JSON-only); recorded as an assumption.
- **Roles:** `ROLE_ADMINISTRATOR`, `ROLE_VETERINARIAN`; coarse checks via `authorizeHttpRequests` and `@PreAuthorize`; fine checks (assigned vet, BR-14) in services.
- **State machine:** `AppointmentStateMachine` declares every edge (from, to, role, guard) in one table; `AppointmentService.transition` is the only place that changes status. `PAID` is only reachable via `PaymentService` calling the machine with actor `SYSTEM`.
- **Rollback:** `POST .../transitions` with target = previous step and a `rollback=true` flag handled as a separate edge set (admin, reason, not from PAID/CLOSED/CANCELLED/SCHEDULED). Previous step: CHECKED_IN→SCHEDULED, IN_PROGRESS→CHECKED_IN, ON_HOLD→IN_PROGRESS, READY→IN_PROGRESS.
- **Money:** `numeric(12,2)` / `BigDecimal` scale 2. Total is recomputed from line items on each change and stored; `amount_paid` stored and updated with payments.
- **Soft delete:** `deleted_at timestamptz`; repositories filter `deletedAt IS NULL`; appointments reference pets/owners by id and historical reads bypass the filter. Phone uniqueness among active owners via partial unique index; chip uniqueness via unique index across all pets.
- **Concurrency:** `@Version` on Appointment; vet double-booking enforced by service check plus partial unique index `(vet_id, scheduled_at) WHERE status <> 'CANCELLED'`.
- **Time:** injected `Clock`; timestamps `timestamptz`; appointment `scheduledAt` is `OffsetDateTime`/`Instant`.
- **Errors:** `ProblemDetail` with `code` (`VALIDATION_ERROR`, `NOT_FOUND`, `BUSINESS_RULE`, `ILLEGAL_TRANSITION`, `FORBIDDEN`, `UNAUTHORIZED`) and `fieldErrors`. Duplicate phone/chip/code return `409` with `fieldErrors` so the UI can show them next to the field.
- **Pagination:** `page`, `size`, `sort`; response `{content, page, size, totalElements}`.
- **TC-6:** no other data store; PostgreSQL covers every requirement.

## 4. Assumptions
| # | Assumption |
|---|---|
| A1 | Seeded accounts (via Flyway): `admin/admin123`, `vet1/vet123`, `vet2/vet123`; local-development defaults only. Passwords stored as BCrypt hashes in the migration. |
| A2 | Authentication is session-based JSON login rather than HTTP Basic, so the frontend can sign in and out (AC-4). CSRF disabled; SameSite=Lax cookie. |
| A3 | Species enum: DOG, CAT, OTHER. Sex enum: MALE, FEMALE, UNKNOWN. Dosage form is free text. Payment method: CASH, CARD. |
| A4 | Rollback is exposed as a distinct endpoint `POST /api/appointments/{id}/rollback` (reason required) to keep forward transitions and rollbacks separate; forward transitions use `POST /api/appointments/{id}/transitions`. `PAID` is rejected as a target of `/transitions`. |
| A5 | Rollback from `SCHEDULED` is not possible (no earlier step). A partial payment keeps the appointment in `READY`, so payments can exist there. User decision: a rollback from `READY` is rejected with `409 code=ROLLBACK_BLOCKED_BY_PAYMENTS` when `amountPaid > 0`, so the total can never fall below `amountPaid` (BR-16, BR-17, BR-23). |
| A6 | A zero-total `READY` appointment: the administrator records no payment possible (amount must be > 0), so `PAID` for total 0 is reached by the system immediately when the vet marks it `READY` with total 0 (guard "incl. total 0"). This is audited as READY→PAID with actor system user = the acting vet. |
| A7 | Booking is restricted to `scheduledAt` strictly after now (no grace). Same-start double-booking compares exact timestamp (BR-12); appointment duration is not used. |
| A8 | Appointment filters accept date range (`from`, `to` on `scheduledAt`), `status`, `veterinarianId`, `petId`; "My appointments" is `veterinarianId=<own id>` (the `GET /api/auth/me` response includes the employee id). |
| A9 | Line items hold exactly one of `serviceId` / `medicationId`; the performing vet is the assigned vet (current user). Catalog name/code is copied into the line item for readable history. |
| A10 | Prescriptions are editable (add, update, delete) only while `IN_PROGRESS`/`ON_HOLD`; line items can be added and removed under the same rule. |
| A11 | Administrators may read all data; veterinarians may read owners, pets, catalogs and appointments (read-only), because UF-8 says staff search. Writes to owners/pets/catalog/booking are administrator-only. |
| A12 | Deleting a pet with an open (not CLOSED/CANCELLED) appointment is rejected as well (analogous to BR-7); TASK.md only states the rule for owners. |
| A13 | Deleting an owner soft-deletes their pets in the same transaction. |

## 5. Implementation Increments

### Increment 1 — Shared foundation (errors, pagination, clock, OpenAPI)
- **Status:** `pending`
- **Goal:** Add the cross-cutting pieces every feature uses: domain exceptions, one `ProblemDetail` handler, paging wrapper, `Clock`, OpenAPI metadata. Make the Testcontainers config reusable.
- **Dependencies:** None
- **Acceptance criteria covered:** AC-1 (starts and applies migrations), AC-24 (test infrastructure); rules: BR-18 (clear error format), TC-6 (PostgreSQL only)

#### API provided
- `GET /api/health` → unchanged `200 {status, service}`; public.
- Error contract for all later endpoints: `ProblemDetail {type,title,status,detail,code,fieldErrors?}`; `400` validation, `404`, `409`.
- `fieldErrors` is a flat object `{ "<fieldName>": "<message>" }`; keys are the request property names (e.g. `phone`, `chipNumber`, `scheduledAt`, `reason`).
- Paging: `page` is zero-based and echoed in the response; default `size` 20, maximum 100 (larger values are clamped, not rejected).

#### Files
**Create:**
- `backend/src/main/java/com/vetclinic/shared/BusinessRuleException.java`, `NotFoundException.java`, `IllegalTransitionException.java` — domain exceptions
- `backend/src/main/java/com/vetclinic/shared/GlobalExceptionHandler.java` — maps exceptions, `MethodArgumentNotValidException`, `ConstraintViolation`, unreadable body, data-integrity violations
- `backend/src/main/java/com/vetclinic/shared/PageResponse.java` — `{content,page,size,totalElements}` record
- `backend/src/main/java/com/vetclinic/config/ClockConfig.java` — `Clock` bean (UTC)
- `backend/src/main/java/com/vetclinic/config/OpenApiConfig.java` — title, version, session security scheme
- `backend/src/test/java/com/vetclinic/shared/GlobalExceptionHandlerTests.java`
**Modify:**
- `backend/src/test/java/com/vetclinic/TestcontainersConfiguration.java` — make `public` so sub-packages can import it
- `backend/src/main/resources/application.yml` — `spring.jackson` / `spring.jpa` time-zone UTC settings, springdoc paths

#### Tasks
- Implement handler returning RFC 9457 bodies with `code` and `fieldErrors`.
- Implement `PageResponse.from(Page<T>)`; configure `spring.data.web.pageable` with `one-indexed-parameters: false`, `default-page-size: 20`, `max-page-size: 100`.
- Expose Swagger UI at `/swagger-ui.html`.

#### Tests
- `GlobalExceptionHandlerTests` (`@WebMvcTest` with a test controller): validation `400` with flat `fieldErrors` map keyed by property name (e.g. `phone`), page `size=500` clamped to 100, default size 20 and zero-based page echoed, `NotFoundException`→404, `BusinessRuleException`→409, `IllegalTransitionException`→409 `ILLEGAL_TRANSITION`, malformed JSON→400.
- Existing `HealthControllerTests` and context test still pass.

#### Completion criteria
- `cd backend && ./mvnw verify` passes.
- `./mvnw spring-boot:run` starts, `GET http://localhost:8080/api/health` returns `200`, `/swagger-ui.html` loads.

---

### Increment 2 — Employees, seed accounts and authentication
- **Status:** `pending`
- **Goal:** Employee table with seeded administrator and two veterinarians, session sign-in/sign-out, and role-based protection of the whole API.
- **Dependencies:** Increment 1
- **Acceptance criteria covered:** AC-3 (only sign-in and health public), AC-4 (sign in/out of seeded accounts); rules: BR-23 (admin-only foundation), BR-14 (role identity)

#### API provided
- `POST /api/auth/login` → `{username,password}` / `200 {id,fullName,role,specialization}` + session cookie / `401` bad credentials, `400` blank fields.
- `POST /api/auth/logout` → none / `204` / session invalidated.
- `GET /api/auth/me` → none / `200 {id,fullName,role,specialization}` / `401`.
- `GET /api/employees?role=VETERINARIAN` → none / `200 [{id,fullName,specialization}]` / `401`. Any signed-in user.
- Any other `/api/**` (except `/api/health`, `/api/auth/login`, Swagger/OpenAPI docs, actuator health) → `401` when unauthenticated, `403` when role not allowed.

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V2__employees.sql` — `employee` table (id, full_name, role, specialization, username unique, password_hash, active) and seed of 1 admin + 2 vets (BCrypt)
- `backend/src/main/java/com/vetclinic/employee/Employee.java`, `Role.java`, `EmployeeRepository.java`, `EmployeeController.java`, `EmployeeResponse.java`
- `backend/src/main/java/com/vetclinic/security/AuthController.java`, `LoginRequest.java`, `EmployeeUserDetailsService.java`, `CurrentUser.java` (resolves the signed-in employee)
- Tests: `AuthControllerTests`, `SecurityAccessIntegrationTests`, `EmployeeSeedIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/config/SecurityConfig.java` — session auth, JSON 401/403 via ProblemDetail, `PasswordEncoder`, CORS `allowCredentials(true)`, method security
- `backend/src/main/resources/application.yml` — session cookie same-site setting

#### Tasks
- Entry points return ProblemDetail JSON (no redirects/basic prompt).
- Public matchers: `/api/health`, `/api/auth/login`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health`.
- `@EnableMethodSecurity`.

#### Tests
- Login succeeds for admin and both vets; wrong password→401; logout invalidates the session (`/me`→401).
- Unauthenticated `GET /api/employees`→401; `/api/health` public.
- Seed: exactly 1 administrator and 2 veterinarians exist.

#### Completion criteria
- `./mvnw verify` passes.
- Manual: `curl -c jar -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' localhost:8080/api/auth/login` returns `200`.

---

### Increment 3 — Owners
- **Status:** `pending`
- **Goal:** Administrator CRUD, search and soft delete of owners with phone uniqueness among active owners.
- **Dependencies:** Increment 2
- **Acceptance criteria covered:** AC-5 (create, edit, list, search, soft-delete owners; duplicate active phone rejected), AC-7 (soft-deleted owners hidden from lists/search); rules: UF-1, BR-1, BR-2, BR-6, TC-2, TC-3

#### API provided
- `POST /api/owners` → `{fullName,phone,email?,address?}` / `201` + `Location` `OwnerResponse` / `400` (blank name/phone, bad email), `409` duplicate active phone (`fieldErrors.phone`); ADMIN.
- `GET /api/owners?query=&page=&size=&sort=` → `200 PageResponse<OwnerSummary>` filter by name or phone substring, excludes deleted; ADMIN, VET.
- `GET /api/owners/{id}` → `200 OwnerResponse` / `404` if soft-deleted (historical access happens via appointments); ADMIN, VET.
- `PUT /api/owners/{id}` → same body as create / `200` / `400`, `404`, `409`; ADMIN.
- `DELETE /api/owners/{id}` → `204` / `404`; ADMIN. (Open-appointment guard added in Increment 6.)

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V3__owners.sql` — `owner` table, `deleted_at`, partial unique index on `phone WHERE deleted_at IS NULL`, indexes on phone and lower(full_name)
- `backend/src/main/java/com/vetclinic/owner/Owner.java`, `OwnerRepository.java`, `OwnerService.java`, `OwnerController.java`, `OwnerRequest.java`, `OwnerResponse.java`, `OwnerSummary.java`
- Tests: `OwnerServiceTests`, `OwnerControllerTests`, `OwnerIntegrationTests`

#### Tasks
- Bean Validation: `@NotBlank` name/phone, `@Email` optional email.
- Phone normalized by trimming; uniqueness checked in service and backed by the DB index.
- Soft delete sets `deletedAt` from `Clock`; reuse of a deleted owner's phone allowed.

#### Tests
- Create valid; blank name, blank phone, invalid email rejected; email omitted accepted.
- Duplicate phone among active→409; same phone as a soft-deleted owner→allowed; edit to another owner's phone→409; edit keeping own phone→OK.
- Soft-deleted owner absent from list and search and `GET`→404.
- Vet cannot create/edit/delete (403); unauthenticated 401.

#### Completion criteria
- `./mvnw verify` passes; Testcontainers integration test proves the partial unique index.

---

### Increment 4 — Pets
- **Status:** `pending`
- **Goal:** Add, list, edit and soft-delete pets under an owner with chip uniqueness and date/weight validation.
- **Dependencies:** Increment 3
- **Acceptance criteria covered:** AC-6 (add/edit pets; duplicate chip and future birth date rejected), AC-7 (soft-deleted pets hidden); rules: UF-1, BR-3, BR-4, BR-5, BR-6, TC-2, TC-3

#### API provided
- `POST /api/owners/{ownerId}/pets` → `{name,species,breed?,sex,dateOfBirth?,weight?,coloring?,chipNumber?}` / `201` `PetResponse` / `400`, `404` owner missing or deleted, `409` duplicate chip (`fieldErrors.chipNumber`); ADMIN.
- `GET /api/owners/{ownerId}/pets` → `200 [PetResponse]` excluding deleted; ADMIN, VET.
- `GET /api/pets/{id}` → `200 PetResponse` / `404`; ADMIN, VET.
- `PUT /api/pets/{id}` → same body / `200` / `400`, `404`, `409`; ADMIN. Owner cannot be changed.
- `DELETE /api/pets/{id}` → `204` / `404`; ADMIN.
- `GET /api/pets?query=&page=&size=` → paged `PetSummary`, excludes deleted; ADMIN, VET.

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V4__pets.sql` — `pet` table, FK to owner, `deleted_at`, unique index on `chip_number` (all pets, nulls allowed), indexes on `chip_number` and lower(`name`)
- `backend/src/main/java/com/vetclinic/pet/Pet.java`, `Species.java`, `Sex.java`, `PetRepository.java`, `PetService.java`, `PetController.java`, `PetRequest.java`, `PetResponse.java`, `PetSummary.java`
- Tests: `PetServiceTests`, `PetControllerTests`, `PetIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/owner/OwnerService.java` — owner delete also soft-deletes the owner's pets (A13)

#### Tasks
- `@PastOrPresent` date of birth; `@Positive` weight; required name, species, sex.
- Chip uniqueness counts soft-deleted pets (BR-5 "across all pets").

#### Tests
- Valid pet; missing name/species/sex; DOB tomorrow rejected, DOB today accepted; weight 0 and negative rejected, null accepted.
- Duplicate chip→409 including against a soft-deleted pet; chip omitted on two pets OK.
- Pet on missing or deleted owner→404.
- Deleted pet hidden from owner pet list, search and `GET`; owner delete cascades to pets.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 5 — Service and medication catalogs
- **Status:** `pending`
- **Goal:** Administrator-managed catalogs with unique codes, non-negative prices and activate/deactivate.
- **Dependencies:** Increment 2
- **Acceptance criteria covered:** AC-9 (manage services and medications, deactivate); rules: BR-8, BR-9, BR-10 (snapshot enforced in Increment 8), TC-4

#### API provided
- `POST /api/services` → `{code,name,durationMinutes,price}` / `201` `ServiceResponse` / `400`, `409` duplicate code; ADMIN.
- `GET /api/services?active=&query=&page=&size=` → `200 PageResponse`; ADMIN, VET.
- `GET /api/services/{id}` → `200` / `404`; ADMIN, VET.
- `PUT /api/services/{id}` → same body / `200` / `400`, `404`, `409`; ADMIN.
- `POST /api/services/{id}/activation` → `{active:boolean}` / `200`; ADMIN.
- `POST /api/medications` → `{sku,name,dosageForm,price}` · `GET /api/medications` · `GET /api/medications/{id}` · `PUT /api/medications/{id}` · `POST /api/medications/{id}/activation` — same shape and errors as services.
- No `DELETE` exists (BR-9).

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V5__catalog.sql` — `service_item`, `medication`, unique code/sku, `numeric(12,2)` with `CHECK (price >= 0)`, seed of a few active services (examination, vaccination, ultrasound, surgery) and medications
- `backend/src/main/java/com/vetclinic/catalog/ServiceItem.java`, `Medication.java`, their repositories, services, controllers, request/response records
- Tests: `ServiceCatalogServiceTests`, `MedicationServiceTests`, `CatalogControllerTests`, `CatalogIntegrationTests`

#### Tasks
- `@DecimalMin("0.00")`, scale 2 normalization; `durationMinutes` positive.
- Code/SKU immutable after creation (assumption folded in request validation: update ignores it); document in OpenAPI.

#### Tests
- Create OK; duplicate code/SKU→409; price 0 accepted, -0.01 rejected; missing fields→400.
- Deactivate and reactivate; list filter by `active`; no DELETE route (405/404).
- Vet read OK, vet write→403.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 6 — Appointment booking, listing and open-appointment guards
- **Status:** `pending`
- **Goal:** Create appointments in `SCHEDULED`, list/filter them, read detail, and add the BR-7 owner delete guard.
- **Dependencies:** Increments 2, 4
- **Acceptance criteria covered:** AC-8 (owner with open appointment cannot be deleted), AC-10 (book; past date and vet double-booking rejected), AC-11 (list and filters), AC-7 (past appointments of deleted owner/pet remain viewable); rules: UF-2, BR-3, BR-6, BR-7, BR-11, BR-12

#### API provided
- `POST /api/appointments` → `{petId,veterinarianId,scheduledAt,complaint?}` / `201` + `Location` `AppointmentResponse` (status `SCHEDULED`, createdBy = current admin) / `400` (missing fields), `404` pet or vet missing/deleted, `409` `code=PAST_DATE` or `DOUBLE_BOOKING` (`fieldErrors.scheduledAt`); ADMIN.
- `GET /api/appointments?from=&to=&status=&veterinarianId=&petId=&page=&size=&sort=` → `200 PageResponse<AppointmentSummary>` where `AppointmentSummary = {id, scheduledAt, status, pet{id,name,deleted}, owner{id,fullName,deleted}, veterinarian{id,fullName}, total}` (same nested objects as the `AppointmentDetail` header); ADMIN, VET. Includes appointments of soft-deleted pets/owners.
- `GET /api/appointments/{id}` → `200 AppointmentDetail` (header, clinical fields, lineItems, prescriptions, payments, total, amountPaid, balance, cancellationReason; line/payment arrays empty until later increments) / `404`; ADMIN, VET.
- `DELETE /api/owners/{id}` (modified) → `409 code=OWNER_HAS_OPEN_APPOINTMENTS` when any pet has an appointment not `CLOSED`/`CANCELLED`. Same for `DELETE /api/pets/{id}` (A12).

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V6__appointments.sql` — `appointment` (pet_id, veterinarian_id, created_by_id, scheduled_at, complaint, anamnesis, diagnosis, notes, status, total numeric(12,2), amount_paid numeric(12,2), cancellation_reason, version, timestamps), partial unique index `(veterinarian_id, scheduled_at) WHERE status <> 'CANCELLED'`, indexes on status, scheduled_at, pet_id
- `backend/src/main/java/com/vetclinic/appointment/Appointment.java`, `AppointmentStatus.java`, `AppointmentRepository.java` (Specification-based filter), `AppointmentService.java`, `AppointmentController.java`, `CreateAppointmentRequest.java`, `AppointmentSummary.java`, `AppointmentDetail.java`
- Tests: `AppointmentBookingServiceTests`, `AppointmentControllerTests`, `AppointmentQueryIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/owner/OwnerService.java`, `pet/PetService.java` — BR-7 guard through an `AppointmentRepository` query

#### Tasks
- Vet must have role VETERINARIAN; booking time compared with `Clock` (BR-11).
- Detail read loads pet/owner including soft-deleted rows.

#### Tests
- Booking OK sets `SCHEDULED` and creator; past time→409; time equal to now rejected (A7); missing pet/vet→400/404; non-vet employee as vet→400.
- Double-booking same vet and instant→409; same instant different vet OK; same instant after cancelled one OK (using a cancelled fixture).
- Summary JSON contains ids and `deleted` flags for pet and owner (true for a soft-deleted pet/owner).
- Filters: each of date range, status, vet, pet and a combination; pagination.
- Owner delete blocked with `SCHEDULED`, `IN_PROGRESS`, `PAID` appointments; allowed when all `CLOSED`/`CANCELLED`.
- Soft-deleted pet's appointment remains readable.
- Vet cannot book (403).

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 7 — State machine, transitions, rollback and audit writing
- **Status:** `pending`
- **Goal:** Single `AppointmentStateMachine` with all edges and guards; transition and rollback endpoints; audit entries written in the same transaction; terminal-state immutability.
- **Dependencies:** Increment 6
- **Acceptance criteria covered:** AC-12 (every forward transition works for the right role, others rejected), AC-13 (cancel with reason from SCHEDULED/CHECKED_IN only), AC-14 (hold/resume), AC-18 (close PAID; CLOSED/CANCELLED immutable), AC-19 (admin rollback with reason; vets cannot), AC-20 (audit written for status changes), AC-23 (server-side `allowedActions`), AC-24 (exhaustive transition tests); rules: UF-3, UF-4, UF-5, UF-6, UF-7, BR-14, BR-18, BR-19, BR-20, BR-22, BR-23, BR-24, BR-25

#### API provided
- `POST /api/appointments/{id}/transitions` → `{target, reason?}` with target in `CHECKED_IN, IN_PROGRESS, ON_HOLD, READY, CANCELLED, CLOSED` (resume is target `IN_PROGRESS` from `ON_HOLD`) / `200 AppointmentDetail` / `400` bad target, `403` wrong role or not the assigned vet, `404`, `409 ILLEGAL_TRANSITION` (edge not in machine), `409 code=REASON_REQUIRED` (cancel), `409 code=DIAGNOSIS_REQUIRED` (to `READY`). `PAID` as target→`409 ILLEGAL_TRANSITION`.
- `POST /api/appointments/{id}/rollback` → `{reason}` / `200 AppointmentDetail` / `400` blank reason, `403` vet, `409 ILLEGAL_TRANSITION` (from SCHEDULED, PAID, CLOSED, CANCELLED), `409 code=ROLLBACK_BLOCKED_BY_PAYMENTS` (from READY when `amountPaid > 0`).

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V7__audit.sql` — `audit_entry` (id, appointment_id, actor_id, occurred_at, type STATUS_CHANGE/PAYMENT/ROLLBACK, old_value, new_value, reason); trigger or rule rejecting UPDATE and DELETE (BR-25)
- `backend/src/main/java/com/vetclinic/appointment/AppointmentStateMachine.java` — edge table `(from,to,allowedActor,guard)`; methods `validate(appointment, target, actor, reason)` and `previousStep(status)`
- `backend/src/main/java/com/vetclinic/appointment/TransitionRequest.java`, `RollbackRequest.java`, `Actor.java` (ADMIN, ASSIGNED_VET, SYSTEM)
- `backend/src/main/java/com/vetclinic/audit/AuditEntry.java`, `AuditEntryType.java`, `AuditRepository.java`, `AuditService.java`
- Tests: `AppointmentStateMachineTests`, `AppointmentTransitionServiceTests`, `AppointmentTransitionControllerTests`, `AppointmentLifecycleIntegrationTests`, `AuditAppendOnlyIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/appointment/AppointmentService.java` — `transition()` and `rollback()` delegate to the machine, save, call `AuditService`; reject any modification of terminal appointments
- `backend/src/main/java/com/vetclinic/appointment/AppointmentController.java` — two endpoints

#### Tasks
- Edge table matches TASK.md exactly: SCHEDULED→CHECKED_IN (admin); SCHEDULED/CHECKED_IN→CANCELLED (admin, reason); CHECKED_IN→IN_PROGRESS, IN_PROGRESS↔ON_HOLD, IN_PROGRESS→READY (assigned vet; READY needs diagnosis); PAID→CLOSED (admin); READY→PAID (SYSTEM only, used in Increment 9).
- Cancel stores `cancellationReason`.
- Rollback creates a ROLLBACK audit entry with old/new status and reason.
- Role/ownership check outcome: wrong role→403; assigned-vet violation→403; wrong state→409.
- Optimistic lock failure→409.
- `AppointmentDetail.allowedActions` is a `List<AllowedAction>`; `AllowedAction` is a Java enum in `com.vetclinic.appointment`, serialized by name, with exactly ten values: `CHECK_IN, START_EXAM, PUT_ON_HOLD, RESUME, READY_FOR_DISCHARGE, CANCEL, CLOSE, ROLLBACK, RECORD_PAYMENT, EDIT_CLINICAL`. It is computed by `AppointmentStateMachine.allowedActions(appointment, currentUser)` (server-side basis of AC-23/UI-2):
  - `CHECK_IN` (admin, SCHEDULED); `CANCEL` (admin, SCHEDULED or CHECKED_IN); `CLOSE` (admin, PAID).
  - `START_EXAM` (assigned vet, CHECKED_IN); `PUT_ON_HOLD` (assigned vet, IN_PROGRESS); `RESUME` (assigned vet, ON_HOLD); `READY_FOR_DISCHARGE` (assigned vet, IN_PROGRESS).
  - `ROLLBACK` (admin, only when the rollback is allowed: CHECKED_IN, IN_PROGRESS, ON_HOLD, or READY with `amountPaid = 0`).
  - `EDIT_CLINICAL` (assigned vet, IN_PROGRESS or ON_HOLD).
  - `RECORD_PAYMENT` (admin, READY with balance > 0).
  - Terminal states (PAID has only `CLOSE` for admin; CLOSED and CANCELLED) return an empty list for everyone else; a non-assigned vet gets an empty list. Order is the enum declaration order.
  - Covered by a unit test per status and role (admin, assigned vet, other vet).

#### Tests
- State machine unit tests: every allowed edge succeeds with the right actor; every (from,to) pair of the 8x8 matrix not in the table is rejected; wrong actor for each allowed edge rejected; cancel without/blank reason rejected; READY without diagnosis rejected, with diagnosis accepted; terminal states reject everything; rollback map for each status including PAID/CLOSED/CANCELLED/SCHEDULED rejected.
- Integration: full happy path SCHEDULED→CHECKED_IN→IN_PROGRESS→ON_HOLD→IN_PROGRESS→READY with each step creating one audit entry (user, time, old, new, reason); another vet cannot start exam; admin cannot start exam; vet cannot rollback or cancel.
- Audit rows cannot be updated or deleted through SQL (DB rejects).
- `allowedActions` per status and role: exact expected list for each of the 8 statuses as admin, assigned vet and other vet; only the ten enum names are ever emitted.

#### Completion criteria
- `./mvnw verify` passes; transition matrix tests listed individually in the Surefire report.

---

### Increment 8 — Clinical data, line items and prescriptions
- **Status:** `pending`
- **Goal:** Let the assigned vet record clinical fields, line items (with price snapshot) and prescriptions only in `IN_PROGRESS`/`ON_HOLD`; keep total consistent.
- **Dependencies:** Increments 5, 7
- **Acceptance criteria covered:** AC-15 (clinical edits only own appointment and only in `IN_PROGRESS`/`ON_HOLD`), AC-16 (total = sum of line items; catalog price changes do not affect existing items), AC-9 (inactive items cannot be added), AC-18 (no changes in terminal states); rules: UF-3, BR-9, BR-10, BR-13, BR-14, BR-15, BR-22

#### API provided
- `PUT /api/appointments/{id}/clinical` → `{anamnesis?,diagnosis?,notes?}` / `200 AppointmentDetail` / `403` not assigned vet or admin, `404`, `409 code=NOT_EDITABLE` (status not `IN_PROGRESS`/`ON_HOLD`); VET.
- `POST /api/appointments/{id}/line-items` → `{serviceId|medicationId (exactly one), quantity}` / `201 LineItemResponse` (unitPrice copied, lineTotal, name, performedBy) / `400` quantity < 1 or both/neither ids, `404` catalog item, `403`, `409 NOT_EDITABLE` or `INACTIVE_CATALOG_ITEM`; VET.
- `DELETE /api/appointments/{id}/line-items/{itemId}` → `204` / `403`, `404`, `409 NOT_EDITABLE`; VET.
- `POST /api/appointments/{id}/prescriptions` → `{medicationName,dosage,frequency,duration,instructions?}` / `201 PrescriptionResponse` / `400`, `403`, `409 NOT_EDITABLE`; VET.
- `PUT /api/appointments/{id}/prescriptions/{rxId}` → same body / `200`; `DELETE .../prescriptions/{rxId}` → `204`; same errors.

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V8__line_items_prescriptions.sql` — `line_item` (appointment_id, service_id nullable, medication_id nullable, `CHECK` exactly one, description snapshot, quantity `CHECK > 0`, unit_price, line_total numeric(12,2), performed_by_id), `prescription`
- `backend/src/main/java/com/vetclinic/appointment/LineItem.java`, `LineItemRepository.java`, `Prescription.java`, `PrescriptionRepository.java`, `ClinicalService.java`, `ClinicalController.java`, request/response records
- Tests: `ClinicalServiceTests`, `ClinicalControllerTests`, `AppointmentTotalsIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/appointment/AppointmentDetail.java` — populated line items/prescriptions

#### Tasks
- One shared `assertEditable(appointment, currentUser)` guard (status check + assigned vet) used by every clinical mutation.
- Recompute and persist `total = Σ lineTotal` after add/remove.
- Administrators cannot edit clinical data (BR-14).

#### Tests
- Edit allowed in IN_PROGRESS and ON_HOLD; rejected in SCHEDULED, CHECKED_IN, READY, PAID, CLOSED, CANCELLED.
- Non-assigned vet→403; administrator→403.
- Line item: quantity 0, -1 rejected; both ids or neither rejected; inactive service/medication rejected; unit price copied; total updated on add and on remove; 3 items sum correctly with decimals (e.g. 19.99 × 3).
- Price change of catalog item after adding leaves line item and total unchanged.
- Prescription add/update/delete with the same status/ownership matrix; missing required fields→400.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 9 — Payments
- **Status:** `pending`
- **Goal:** Record payments on `READY` appointments, compute balance, move to `PAID` exactly at zero, audit each payment.
- **Dependencies:** Increments 7, 8
- **Acceptance criteria covered:** AC-17 (payments only in READY, not above balance, PAID exactly at zero), AC-20 (payments audited), AC-18 (no payment on terminal states), AC-12 (READY→PAID system transition); rules: UF-5, BR-16, BR-17, BR-21, BR-22, BR-24, TC-4

#### API provided
- `POST /api/appointments/{id}/payments` → `{amount, method: CASH|CARD}` / `201 PaymentResponse {id,amount,paidAt,method,recordedBy}` plus appointment `status`, `amountPaid`, `balance` in `AppointmentDetail` returned by GET / `400` amount ≤ 0 or bad method, `404`, `403` vet, `409 code=PAYMENT_NOT_ALLOWED` (status not READY), `409 code=OVERPAYMENT` (amount > balance); ADMIN.
- `GET /api/appointments/{id}/payments` → `200 [PaymentResponse]`; ADMIN, VET.

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V9__payments.sql` — `payment` table, `CHECK (amount > 0)`
- `backend/src/main/java/com/vetclinic/payment/Payment.java`, `PaymentMethod.java`, `PaymentRepository.java`, `PaymentService.java`, `PaymentController.java`, `PaymentRequest.java`, `PaymentResponse.java`
- Tests: `PaymentServiceTests`, `PaymentControllerTests`, `PaymentFlowIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/appointment/AppointmentService.java` — `markReady` with total 0 triggers system `READY→PAID` (A6); `AppointmentDetail` shows payments and balance

#### Tasks
- Payment + `amountPaid` update + audit entry (PAYMENT type, old/new amount paid) + optional `READY→PAID` status audit in one transaction.
- Lock the appointment row (`PESSIMISTIC_WRITE` or version check) to prevent concurrent overpayment.

#### Tests
- Payment in each non-READY status rejected; amount 0 and negative rejected; amount equal to balance → PAID; amount one cent over balance→409; partial payment keeps READY; two partial payments summing to total → PAID; payment after PAID rejected.
- Total 0 appointment becomes PAID when marked READY (A6).
- Audit entries for payments and PAID transition with amounts and user.
- Vet cannot pay (403).
- Rollback from PAID is rejected (BR-23); READY→IN_PROGRESS rollback accepted while no payments exist; after a partial payment the same rollback returns `409 ROLLBACK_BLOCKED_BY_PAYMENTS` and the status stays `READY`.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 10 — Audit log API
- **Status:** `pending`
- **Goal:** Expose a filterable, read-only audit log for administrators.
- **Dependencies:** Increments 7, 9
- **Acceptance criteria covered:** AC-20 (audit visible with user, time, old/new values, reason); rules: UF-7, BR-24, BR-25

#### API provided
- `GET /api/audit-entries?appointmentId=&actorId=&type=&from=&to=&page=&size=&sort=occurredAt,desc` → `200 PageResponse<AuditEntryResponse {id,appointmentId,actor{id,fullName},occurredAt,type,oldValue,newValue,reason}>`; ADMIN only (`403` vet).
- `GET /api/appointments/{id}/audit-entries` → `200 [AuditEntryResponse]` status history for the detail page; ADMIN, VET.
- No `POST`, `PUT`, `PATCH`, `DELETE` routes exist.

#### Files
**Create:**
- `backend/src/main/java/com/vetclinic/audit/AuditController.java`, `AuditEntryResponse.java`, `AuditQueryService.java`
- Tests: `AuditControllerTests`, `AuditLogIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/audit/AuditRepository.java` — Specification/filter queries
- `backend/src/main/resources/db/migration/` — new `V10__audit_indexes.sql` for `appointment_id`, `occurred_at` indexes

#### Tasks
- Newest first by default; filter by type.

#### Tests
- Each filter alone and combined; ordering; vet→403; per-appointment history lists transitions, rollback and payments in order; write methods return 405.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 11 — Global search and medical history
- **Status:** `pending`
- **Goal:** Single search across owners and pets, owner card timeline and pet card chronological medical history.
- **Dependencies:** Increments 6, 8
- **Acceptance criteria covered:** AC-22 (search by owner name, phone, pet name, chip), AC-21 (pet history chronological), AC-7 (deleted hidden from search, history intact); rules: UF-8, BR-6, TC-2

#### API provided
- `GET /api/search?q=&limit=` → `200 {owners:[OwnerSummary], pets:[PetSummary with ownerName]}` matching owner name/phone and pet name/chip (case-insensitive, partial; chip exact or prefix), excluding soft-deleted; `400` when `q` shorter than 2 chars; ADMIN, VET.
- `GET /api/pets/{id}/history` → `200 [HistoryEntry {appointmentId,scheduledAt,status,veterinarian,complaint,anamnesis,diagnosis,notes,lineItems,prescriptions}]` ascending by `scheduledAt`; includes closed/cancelled; works also for a soft-deleted pet when requested by id from an appointment link (`200`), while `GET /api/pets/{id}` stays `404`; ADMIN, VET.
- `GET /api/owners/{id}/appointments` → `200 [AppointmentSummary]` timeline for the owner card, ascending by `scheduledAt`; ADMIN, VET.

#### Files
**Create:**
- `backend/src/main/resources/db/migration/V11__search_indexes.sql` — only indexes not already present (e.g. `pg_trgm` is NOT used; plain lower() btree indexes)
- `backend/src/main/java/com/vetclinic/search/SearchController.java`, `SearchService.java`, `SearchResponse.java`
- `backend/src/main/java/com/vetclinic/pet/PetHistoryService.java`, `HistoryEntry.java`
- Tests: `SearchServiceTests`, `SearchControllerTests`, `SearchHistoryIntegrationTests`
**Modify:**
- `backend/src/main/java/com/vetclinic/pet/PetController.java`, `owner/OwnerController.java` — history and timeline endpoints

#### Tasks
- Reuse repository queries from Increments 3-4; do not duplicate soft-delete filters.

#### Tests
- Find by owner name fragment, phone, pet name, exact chip; no match returns empty arrays; deleted owner and pet not found; history order with three appointments out of insertion order; deleted pet's history still reachable by id; unauthenticated 401.

#### Completion criteria
- `./mvnw verify` passes.

---

### Increment 12 — Hardening, API documentation and end-to-end verification
- **Status:** `pending`
- **Goal:** Confirm the whole backend satisfies the API-verifiable criteria, document the contract and close test gaps.
- **Dependencies:** Increments 1-11
- **Acceptance criteria covered:** AC-1 (run starts, migrations applied), AC-2 (CORS with credentials for `http://localhost:3000`), AC-3 (endpoint sweep for 401), AC-12 (exhaustive transition regression through HTTP), AC-24 (rules and state machine coverage, Testcontainers only); rules: BR-18, BR-19, TC-1, TC-5

#### API provided
- No new endpoints; OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` list all operations from section 6.

#### Files
**Create:**
- `backend/src/test/java/com/vetclinic/security/EndpointSecuritySweepTests.java` — every published route unauthenticated→401, vet-forbidden routes→403
- `backend/src/test/java/com/vetclinic/appointment/FullVisitFlowIntegrationTests.java` — UF-2 to UF-5 through HTTP
- `backend/src/test/java/com/vetclinic/config/CorsTests.java`
**Modify:**
- `backend/src/main/java/com/vetclinic/config/OpenApiConfig.java` / controllers — missing summaries on unclear operations
- `README.md` — only if run commands for the backend changed (default: none)

#### Tasks
- Verify there is no code outside `AppointmentStateMachine` that assigns `status` (search; a test using ArchUnit-style reflection is not added to avoid a dependency; a grep check is recorded in PROGRESS).
- Verify tests never reference the compose database.

#### Tests
- Sweep test over all mappings in `RequestMappingHandlerMapping`.
- Full visit flow: book, check-in, start, clinical data, line items, ready, partial and final payment, close, then confirm a further change returns 409.
- CORS preflight from `http://localhost:3000` returns `Access-Control-Allow-Credentials: true`; other origin rejected.

#### Completion criteria
- `cd backend && ./mvnw verify` passes with all increments' tests.
- `docker compose up -d` then `./mvnw spring-boot:run` starts cleanly and `GET /api/health` returns `200`.

## 6. API Contract

All endpoints are under `/api`, JSON, session cookie auth (Increment 2). Errors use `ProblemDetail` (`type,title,status,detail,code,fieldErrors?`). Unauthenticated → `401`; wrong role → `403`; missing or soft-deleted → `404`; business rule → `409`; validation → `400`. Lists return `{content,page,size,totalElements}` unless noted; `page` is zero-based, default `size` 20, maximum 100 (clamped). `fieldErrors` is a flat object `{ "<requestProperty>": "<message>" }`. `AppointmentSummary` = `{id,scheduledAt,status,pet{id,name,deleted},owner{id,fullName,deleted},veterinarian{id,fullName},total}`.

| Method | Path | Request | Response | Errors | Auth |
|---|---|---|---|---|---|
| GET | `/api/health` | — | `200 {status,service}` | — | public |
| POST | `/api/auth/login` | `{username,password}` | `200 EmployeeResponse` + cookie | 400, 401 | public |
| POST | `/api/auth/logout` | — | `204` | 401 | any signed in |
| GET | `/api/auth/me` | — | `200 {id,fullName,role,specialization}` | 401 | any |
| GET | `/api/employees?role=` | — | `200 [{id,fullName,specialization}]` | 401 | any |
| POST | `/api/owners` | `{fullName,phone,email?,address?}` | `201 OwnerResponse` | 400, 409 phone | ADMIN |
| GET | `/api/owners?query=` | — | `200 PageResponse<OwnerSummary>` | 401 | ADMIN, VET |
| GET | `/api/owners/{id}` | — | `200 OwnerResponse` | 404 | ADMIN, VET |
| PUT | `/api/owners/{id}` | as create | `200` | 400, 404, 409 | ADMIN |
| DELETE | `/api/owners/{id}` | — | `204` | 404, 409 `OWNER_HAS_OPEN_APPOINTMENTS` | ADMIN |
| GET | `/api/owners/{id}/appointments` | — | `200 [AppointmentSummary]` | 404 | ADMIN, VET |
| POST | `/api/owners/{ownerId}/pets` | `{name,species,breed?,sex,dateOfBirth?,weight?,coloring?,chipNumber?}` | `201 PetResponse` | 400, 404, 409 chip | ADMIN |
| GET | `/api/owners/{ownerId}/pets` | — | `200 [PetResponse]` | 404 | ADMIN, VET |
| GET | `/api/pets?query=` | — | `200 PageResponse<PetSummary>` | — | ADMIN, VET |
| GET | `/api/pets/{id}` | — | `200 PetResponse` | 404 | ADMIN, VET |
| PUT | `/api/pets/{id}` | as create | `200` | 400, 404, 409 | ADMIN |
| DELETE | `/api/pets/{id}` | — | `204` | 404, 409 open appointment | ADMIN |
| GET | `/api/pets/{id}/history` | — | `200 [HistoryEntry]` ascending | 404 | ADMIN, VET |
| GET | `/api/search?q=&limit=` | — | `200 {owners,pets}` | 400 | ADMIN, VET |
| POST | `/api/services` | `{code,name,durationMinutes,price}` | `201 ServiceResponse` | 400, 409 | ADMIN |
| GET | `/api/services?active=&query=` | — | `200 PageResponse` | — | ADMIN, VET |
| GET | `/api/services/{id}` | — | `200` | 404 | ADMIN, VET |
| PUT | `/api/services/{id}` | as create | `200` | 400, 404, 409 | ADMIN |
| POST | `/api/services/{id}/activation` | `{active}` | `200` | 404 | ADMIN |
| POST | `/api/medications` | `{sku,name,dosageForm,price}` | `201 MedicationResponse` | 400, 409 | ADMIN |
| GET | `/api/medications?active=&query=` | — | `200 PageResponse` | — | ADMIN, VET |
| GET | `/api/medications/{id}` | — | `200` | 404 | ADMIN, VET |
| PUT | `/api/medications/{id}` | as create | `200` | 400, 404, 409 | ADMIN |
| POST | `/api/medications/{id}/activation` | `{active}` | `200` | 404 | ADMIN |
| POST | `/api/appointments` | `{petId,veterinarianId,scheduledAt,complaint?}` | `201 AppointmentResponse` | 400, 404, 409 `PAST_DATE`/`DOUBLE_BOOKING` | ADMIN |
| GET | `/api/appointments?from=&to=&status=&veterinarianId=&petId=` | — | `200 PageResponse<AppointmentSummary>` | 400 | ADMIN, VET |
| GET | `/api/appointments/{id}` | — | `200 AppointmentDetail` | 404 | ADMIN, VET |
| POST | `/api/appointments/{id}/transitions` | `{target,reason?}` | `200 AppointmentDetail` | 400, 403, 404, 409 `ILLEGAL_TRANSITION`/`REASON_REQUIRED`/`DIAGNOSIS_REQUIRED` | role per edge (ADMIN: CHECKED_IN, CANCELLED, CLOSED; assigned VET: IN_PROGRESS, ON_HOLD, READY) |
| POST | `/api/appointments/{id}/rollback` | `{reason}` | `200 AppointmentDetail` | 400, 403, 404, 409 `ILLEGAL_TRANSITION`/`ROLLBACK_BLOCKED_BY_PAYMENTS` | ADMIN |
| PUT | `/api/appointments/{id}/clinical` | `{anamnesis?,diagnosis?,notes?}` | `200 AppointmentDetail` | 403, 404, 409 `NOT_EDITABLE` | assigned VET |
| POST | `/api/appointments/{id}/line-items` | `{serviceId?|medicationId?,quantity}` | `201 LineItemResponse` | 400, 403, 404, 409 `NOT_EDITABLE`/`INACTIVE_CATALOG_ITEM` | assigned VET |
| DELETE | `/api/appointments/{id}/line-items/{itemId}` | — | `204` | 403, 404, 409 | assigned VET |
| POST | `/api/appointments/{id}/prescriptions` | `{medicationName,dosage,frequency,duration,instructions?}` | `201 PrescriptionResponse` | 400, 403, 404, 409 | assigned VET |
| PUT | `/api/appointments/{id}/prescriptions/{rxId}` | as create | `200` | 400, 403, 404, 409 | assigned VET |
| DELETE | `/api/appointments/{id}/prescriptions/{rxId}` | — | `204` | 403, 404, 409 | assigned VET |
| POST | `/api/appointments/{id}/payments` | `{amount,method}` | `201 PaymentResponse` | 400, 403, 404, 409 `PAYMENT_NOT_ALLOWED`/`OVERPAYMENT` | ADMIN |
| GET | `/api/appointments/{id}/payments` | — | `200 [PaymentResponse]` | 404 | ADMIN, VET |
| GET | `/api/appointments/{id}/audit-entries` | — | `200 [AuditEntryResponse]` | 404 | ADMIN, VET |
| GET | `/api/audit-entries?appointmentId=&actorId=&type=&from=&to=` | — | `200 PageResponse<AuditEntryResponse>` | 403 | ADMIN |

`AppointmentDetail` = `{id,scheduledAt,status,pet{id,name,deleted},owner{id,fullName,phone,deleted},veterinarian{id,fullName},createdBy{id,fullName},complaint,anamnesis,diagnosis,notes,cancellationReason,lineItems[],prescriptions[],payments[],total,amountPaid,balance,allowedActions[]}`. `allowedActions` is a list of exactly these enum names: `CHECK_IN, START_EXAM, PUT_ON_HOLD, RESUME, READY_FOR_DISCHARGE, CANCEL, CLOSE, ROLLBACK, RECORD_PAYMENT, EDIT_CLINICAL`. It is computed by the state machine for the current user and appointment (admin and assigned-vet edges per the TASK.md table; `ROLLBACK` for admins when a rollback is allowed; `EDIT_CLINICAL` for the assigned vet in `IN_PROGRESS`/`ON_HOLD`; `RECORD_PAYMENT` for admins in `READY` with balance > 0; empty for CLOSED, CANCELLED and for users with no permitted action) so the UI can honor UI-2 without duplicating rules.

## 7. Coverage Matrix
| Code | Increment |
|---|---|
| UF-1 | 3, 4 |
| UF-2 | 6 |
| UF-3 | 7, 8 |
| UF-4 | 7 |
| UF-5 | 9, 7 |
| UF-6 | 7 |
| UF-7 | 7, 10 |
| UF-8 | 11 |
| BR-1, BR-2 | 3 |
| BR-3, BR-4, BR-5 | 4 |
| BR-6 | 3, 4, 6, 11 |
| BR-7 | 6 |
| BR-8, BR-9, BR-10 | 5, 8 |
| BR-11, BR-12 | 6 |
| BR-13, BR-14, BR-15 | 8 |
| BR-16, BR-17 | 9 |
| BR-18 to BR-23 | 7 (BR-21 also 9) |
| BR-24, BR-25 | 7, 9, 10 |
| TC-1, TC-5 | 1, 12 |
| TC-2, TC-3 | 3, 4, 11 |
| TC-4 | 5, 9 |
| TC-6 | 1 (Architecture Decisions) |
| AC-1 to AC-24 | AC-1: 1, 12 · AC-2: 12 · AC-3: 2, 12 · AC-4: 2 · AC-5: 3 · AC-6: 4 · AC-7: 3, 4, 6, 11 · AC-8: 6 · AC-9: 5, 8 · AC-10: 6 · AC-11: 6 · AC-12: 7, 9, 12 · AC-13: 7 · AC-14: 7 · AC-15: 8 · AC-16: 8 · AC-17: 9 · AC-18: 7, 8, 9 · AC-19: 7 · AC-20: 7, 9, 10 · AC-21: 11 · AC-22: 11 · AC-23: server side via `allowedActions` (7) · AC-24: 1, 7, 12 |
