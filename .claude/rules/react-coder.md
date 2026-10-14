---
paths:
  - "frontend/app/**"
  - "frontend/components/**"
  - "frontend/lib/**"
  - "frontend/package.json"
  - "frontend/next.config.ts"
---

# React Coder

Production code and configuration of the frontend. Tests follow `react-tester.md`.

## Build and configuration
- Keep Next.js 16, React 19, TypeScript strict and Tailwind CSS 4 unless the user explicitly requests a change.
- Add dependencies with `npm install` so `package-lock.json` stays consistent. No second UI component framework without an approved increment.
- The backend base URL comes only from `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).

## Code
- App Router. Routes in `frontend/app/`, reusable components in `frontend/components/`, API access and helpers in `frontend/lib/`.
- All backend calls go through the typed client in `frontend/lib/api.ts`. Never `fetch` the backend directly from components.
- API DTO types live in `frontend/lib/types.ts` and mirror the backend DTO names and the API Contract.
- `"use client"` only where interactivity is needed.
- Style with Tailwind utility classes. Shared elements (status badge, confirm dialog, empty state, pagination, form field with error) are single components reused everywhere.
- Status badge colors come from one mapping shared by every page.
- Show action buttons only for transitions allowed for the current status and role. The backend still enforces the rules.
- Forms mirror backend validation and show backend `fieldErrors` next to their fields. Server errors are readable messages, never raw JSON.
- Every list has loading, empty and error states. Destructive actions use the shared confirm dialog; cancel and rollback require a reason.
- Accessible (labels, roles, keyboard) and responsive.

## Done
- `npm run lint`, `npm test` and `npm run build` pass.
