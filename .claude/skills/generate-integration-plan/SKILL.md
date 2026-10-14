---
name: generate-integration-plan
description: Generates or regenerates context/integration/PLAN.md from TASK.md (read from the integration perspective), the backend and frontend plans and reviews, and the repo run setup. Writes integration increments (joint startup, shared configuration, end-to-end checks) plus Suggested Integration Adjustments to existing backend and frontend increments. Called by generate-plan integration.
---

# Generate Integration Plan

1. Read `TASK.md` as your agent definition says (section **Reading TASK.md**), `context/backend/PLAN.md` and `context/frontend/PLAN.md`. Missing one → stop and name it. Read their `PLAN_REVIEW.md`, if any.
2. Inspect the run setup listed in your agent definition, skipping dependencies and generated files (`node_modules/`, `target/`, `.next/`, `.git/`, lockfiles…).
3. Read your current `PLAN.md` and `PLAN_REVIEW.md`, if any. Never change an increment that is `in_progress` or `done`.
4. Compare what the backend publishes with what the frontend consumes:
   - **Operations:** method, path, request, response
   - **Data:** fields and types, status values, date and money formats
   - **Errors and pagination**
   - **Authentication:** session, roles, CORS
   - **Connection:** base URL, ports, environment variables
   - **Order:** a frontend increment that needs something the backend delivers later

   Also check what end-to-end flows and startup need from each side. The owner of a gap is the area that deviates from the backend API Contract, or the backend if the contract lacks something.
5. Decide where each gap goes:

   | Gap | Goes to |
   |---|---|
   | Small, concrete change to a `pending` increment of the owner | **Suggested Integration Adjustment** |
   | Large, touches an `in_progress` or `done` increment, or better done once both sides are implemented | **Integration increment** |
   | Already an open finding in the owner's `PLAN_REVIEW.md` | nothing |

6. Write `context/integration/PLAN.md` in the plan format in `.claude/skills/generate-regular-plan/SKILL.md`, plus:
   - Dependencies on existing increments of the other plans: `Backend Increment N`, `Frontend Increment N`.
   - `## 6. Shared Configuration`: addresses, ports and variables with defaults, taken from the backend and frontend plans.
   - `## 7. Suggested Integration Adjustments` (`None.` if empty):

     ```markdown
     ### SA1 - <short title>
     - **Area:** Backend | Frontend
     - **Increment:** <Area> Increment N
     - **Severity:** Blocker | Major | Minor
     - **Problem:** …
     - **Evidence:** <what the backend says vs what the frontend says, or the TASK.md code>
     - **Suggested change:** …
     ```

     **Blocker** if it breaks communication, **Major** if it likely fails an AC or flow, **Minor** otherwise.
7. Each increment is checkable end to end (start command, URL, expected result) and leaves the app working.
8. Cover every TASK.md code you took; add nothing outside it. Never edit other areas' plans or reviews.
9. Run `update-progress`, then `update-changelog`.

**Reply:** the increments (`Increment N | title | dependencies | completion criteria`) and the adjustments (`SAn | area › increment | severity | title`).
