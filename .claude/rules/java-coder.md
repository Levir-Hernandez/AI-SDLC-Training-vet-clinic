---
paths:
  - "backend/src/main/**"
  - "backend/pom.xml"
  - "backend/.mvn/**"
---

# Java Coder

Production code, build and configuration of the backend. Tests follow `java-tester.md`; `compose.yaml` follows `docker-expert.md`.

## Build and configuration
- Keep Java 21 and Spring Boot 4.1.x unless the user explicitly requests an upgrade.
- Add only dependencies required by `TASK.md` or an approved increment; prefer Spring Boot managed starters. Pin any version the Spring Boot BOM does not manage.
- No Lombok, WebFlux, Spring Data REST or another database engine unless an approved increment requires it and records why.
- The database comes from the root `compose.yaml` through Spring Boot Docker Compose support (`spring.docker.compose.file: ../compose.yaml`); do not duplicate the JDBC URL in `application.yml`.
- Schema changes go only through new Flyway migrations `V<n>__<description>.sql` in `db/migration/`. Never edit a committed migration. Hibernate `ddl-auto` stays `validate`.
- Money is `numeric(12,2)` in SQL. Index searched columns (owner phone, pet chip number, pet name).
- Never commit secrets, real credentials or machine-specific absolute paths.

## Code
- Package by feature under `com.vetclinic`: `owner`, `pet`, `catalog`, `appointment`, `payment`, `audit`, `security`, plus `config` and `shared`. Each feature keeps its entity, repository, service, controller and DTOs together.
- Controllers are thin: validate input, call one service method, map the result. Business rules live in services.
- Controllers expose request/response DTOs (Java `record`s); never JPA entities.
- Constructor injection only. No field injection, no static mutable state.
- All appointment status transitions go through one state-machine component (e.g. `AppointmentStateMachine`) that declares every allowed edge with its role and guard. No transition logic in controllers, entity setters or scattered `if`s.
- Money uses `BigDecimal` with scale 2; never `double`/`float`.
- Soft-delete uses a `deletedAt` timestamp; list and search queries exclude soft-deleted rows.
- Rule violations throw domain exceptions (`BusinessRuleException`, `NotFoundException`, `IllegalTransitionException`) mapped by a single `@RestControllerAdvice`.
- Every status change and payment writes an audit entry in the same transaction.
- Modifying service methods are `@Transactional`; read-only ones `@Transactional(readOnly = true)`.
- Use `java.time`; inject a `Clock` wherever "now" matters.
- Cite the BR/AC code in a short comment only where a rule is non-obvious.

## REST API
- Business endpoints live under `/api/...` with plural nouns: `/api/owners`, `/api/pets`, `/api/appointments`, `/api/services`, `/api/medications`, `/api/audit-entries`.
- Nested resources for strong ownership: `/api/owners/{id}/pets`, `/api/appointments/{id}/line-items`, `/api/appointments/{id}/prescriptions`, `/api/appointments/{id}/payments`.
- Status changes are an explicit action: `POST /api/appointments/{id}/transitions` with `{ "target": "CHECKED_IN", "reason": null }`. No generic `PATCH status`.
- Status codes: `201` + `Location` create · `200` read/update · `204` delete · `400` validation · `401`/`403` authentication/authorization · `404` missing or soft-deleted · `409` business-rule conflict.
- Errors are RFC 9457 `ProblemDetail` with a `code` property (e.g. `ILLEGAL_TRANSITION`) and, for validation, a `fieldErrors` map.
- Lists accept `page`, `size`, `sort` and return `{ content, page, size, totalElements }`.
- Validate request DTOs with Jakarta Validation and `@Valid`. Add springdoc annotations only where the default output is unclear.

## Done
- `./mvnw verify` passes.
