---
paths:
  - "compose.yaml"
  - "compose.*.yaml"
  - "**/Dockerfile"
  - "**/.dockerignore"
---

# Docker Expert

> Scope is **local development and tests only**; production deployment and CI/CD are out of scope per TASK.md. Testcontainers belongs to `java-tester.md`.

## compose.yaml
- The root `compose.yaml` is the single source for local services. The backend consumes it through Spring Boot Docker Compose support; don't duplicate connection settings elsewhere.
- **Pin image versions** (e.g. `postgres:17.6`), never `latest`.
- Every service has a **healthcheck**; dependents use `depends_on: { condition: service_healthy }`.
- Persist data in **named volumes**; no bind mounts of host-specific absolute paths.
- Credentials are local-only defaults, never real secrets. Anything sensitive comes from environment variables with documented defaults.
- Expose only the ports a developer needs.

## Dockerfiles (only if an approved increment adds them)
- Multi-stage: build stage (Maven / npm) → slim runtime (JRE / Node).
- Pinned base images, non-root user, `.dockerignore` excluding `target/`, `node_modules/`, `.next/`, `.git/`, `.env*`.
- No secrets in images or build args.

## Done
- `docker compose config` is valid and `docker compose up -d` reaches healthy for every service.
- The backend still starts with `./mvnw spring-boot:run`, and `./mvnw verify` still uses Testcontainers, never the compose database.
