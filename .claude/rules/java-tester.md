---
paths:
  - "backend/src/test/**"
---

# Java Tester

- JUnit 5 + AssertJ (via `spring-boot-starter-*-test`). Test class names end with `Tests`.
- **Unit tests** (no Spring context) for services and the appointment state machine. Mock repositories with Mockito; use a fixed `Clock`.
- **Controller tests** use `@WebMvcTest` + `MockMvc`: validation errors, status codes, `ProblemDetail` payloads and role-based access (`spring-security-test`, `@WithMockUser`).
- **Integration tests** use `@SpringBootTest` with `@Import(TestcontainersConfiguration.class)`, which provides an isolated PostgreSQL Testcontainer through `@ServiceConnection`. Never point tests at the compose database.
- The state machine is tested exhaustively: every allowed transition (right role → success) and representative rejected ones (wrong state, wrong role, missing reason, terminal states).
- Every business rule has a test for the allowed and the rejected case.
- Cover the boundaries named in TASK.md: zero balance, payment equal to and over the balance, past booking time, duplicate chip/phone, soft-deleted records.
- Tests are independent: no ordering, no shared mutable data. Integration tests clean up through `@Transactional` rollback or explicit setup.
- One behavior per test method, named after it, e.g. `cancelWithoutReasonIsRejected`.
- `./mvnw verify` must pass before an increment is marked done.
