---
name: generate-regular-plan
description: Generates or regenerates the calling agent's PLAN.md (backend or frontend) as small, ordered, testable increments from TASK.md (read from the agent's perspective) and its part of the repository. Defines the plan format all area planners use; the agent adds its own folders, sections and fields. Called by generate-plan backend|frontend.
---

# Generate Regular Plan

1. Read `TASK.md` as your agent definition says (section **Reading TASK.md**), and the other inputs it lists. Missing a required one → stop and name it.
2. Inspect your repository folders (listed in your agent definition). Skip dependencies and generated files: `node_modules/`, `target/`, `.next/`, `build/`, `dist/`, `.git/`, `.idea/`, `.mvn/wrapper/`, lockfiles. Record only what matters for planning.
3. Read your current `PLAN.md` and `PLAN_REVIEW.md`, if any. Never change an increment that is `in_progress` or `done`.
4. Write your `PLAN.md` in the format below. Increments are small, ordered (dependencies only point backwards), each testable on its own and leaving the app working. Cover every TASK.md code you took; add nothing outside it.
5. Be concise: no full code, only signatures or sketches where needed.
6. Run `update-progress`, then `update-changelog`.

## Plan format

```markdown
# <Area> Plan — <system name>

## 1. Overview
<what is built, how many increments, approach; 3–5 lines>

## 2. Repository Findings
- <what exists and matters: build files, config, packages/folders, endpoints/pages, tests>
- <what is reused, what must change>

## 3. Architecture Decisions
<package or folder tree with one-line roles, and key decisions>

## 4. Assumptions
| # | Assumption |
|---|---|
| A1 | … |

## 5. Implementation Increments

### Increment 1 — <title>
- **Status:** `pending`
- **Goal:** <1–2 sentences>
- **Dependencies:** None | Increment N | <Area> Increment N
- **Acceptance criteria covered:** AC-n (<short label>), …; rules: BR-n, …

#### Files
**Create:**
- `path` — purpose
**Modify:**
- `path` — change
**Delete:**
- `path` — reason

#### Tasks
- …

#### Tests
- <test class or file>: <scenarios, incl. rejected cases>

#### Completion criteria
- <commands and observable results>
```

- Omit empty **Create / Modify / Delete** groups.
- Add the extra increment fields and plan sections your agent definition asks for.
- Cross-area dependencies name the area: `Backend Increment 3`.
