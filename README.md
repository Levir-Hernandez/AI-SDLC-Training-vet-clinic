## Solution Resume

**Agents**
- `backend-planner`
- `frontend-planner`
- `integration-planner`

**Agent skills**
- `generate-plan <area>`
- `validate-plan <area>`
- `revise-plan <area>`
- `validate-integration`

**Scoped rules**
- `java-coder`, `java-tester`
- `react-coder`, `react-tester`
- `docker-expert`

See [HARNESS_ARCHITECTURE.md](HARNESS_ARCHITECTURE.md) for the structure and the full flow.

### Key takeaways

- **Separate planners for backend, frontend and integration** let each one interpret `TASK.md` according to its expertise and focus on its own part of the solution, instead of one plan trying to cover everything.
- **The integration planner is key.** It reconciles inconsistencies between the backend and frontend plans (endpoints, fields, formats, auth, ports) at planning time, without having to discover them by trial and error during implementation.

### Interesting findings

- I first used a fourth agent, a project planner, that wrote a specific task for each subagent. Curiously, the generated task files stayed almost the same as the original `TASK.md` and rarely got smaller: the AI preferred to pass the complete cases instead of trimming them. The split cost an extra step without a real benefit, so I removed it and let each planner read `TASK.md` directly.

### Challenges encountered

- With a Java backend and a React frontend, following the approach suggested by the task, the plans generated independently usually failed in terms of compatibility: the API the backend published did not match what the frontend consumed. This is what led to the integration planner and its suggested adjustments, which turn those mismatches into review findings for the backend and frontend plans.
