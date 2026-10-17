## Solution Summary

```
.claude/
├── agents/                 
│   ├── planner.md          
│   └── plan-reviewer.md    
├── skills/                 
│   ├── generate-plan/      
│   ├── review-plan/        
│   ├── revise-plan/        
│   ├── update-progress/    
│   └── update-changelog/   
└── rules/                  
    ├── java-coder.md       
    ├── java-tester.md      
    ├── react-coder.md      
    └── react-tester.md     
```

Loop: `/generate-plan` → `/review-plan [approach]` → while the verdict is not `READY`: `/revise-plan [codes] [-- context]` → `/review-plan`.

The harness is described in more detail in `HARNESS_ARCHITECTURE.md`.

## Key Takeaways

- Added an `approach` argument to `/review-plan` to guide the review toward a specific strategy: establish the core domain models and business rules in the initial increments, then apply vertical slicing once that foundation is in place.
- Added a `context` argument to `/revise-plan` to provide additional guidance when the reviewer's proposed solution does not meet the expected criteria or requires assumptions that could affect the plan.
- Added a **Decisions Log** section to `PLAN_REVIEW` to document decisions and assumptions when context is passed to `/revise-plan`.
- These changes reduce manual review effort by guiding the planning and revision processes while preserving control over architectural decisions and dependencies.

## Challenges Encountered

- Using a single planner to create an implementation plan for a project with different technologies, such as separate backend and frontend technologies in this case, can introduce inconsistencies across multiple layers. This problem is not solved simply by using specialized agents. Instead, defining **integration guidelines** increases the likelihood of producing a consistent plan. These guidelines could include basing the frontend on the backend API contract or defining a transversal contract between entities, as in this case with the `appointments` entity.

- The initial `/generate-plan` output I got followed a **vertical-slicing** approach, building each domain entity end to end (model, services, security, UI) before moving on to the next. While this works well for vibe coding, where each increment can be tested and refined immediately, it is less favorable for defining the entire plan upfront. The main drawback is that dependencies between increments are organized per entity rather than by layer or module. As a result, incremental generation is more prone to inconsistencies, and plan revisions tend to affect other increments.

- After manually reviewing the generated plan, I realized that although the plan itself was good, its approach (vertical slicing) was not ideal. However, a simple `/review-plan` did not identify this issue; it merely iterated over the plan and refined it without proposing substantial changes. Since the plan's deliverables were good but poorly organized, I thought there had to be a better alternative to reviewing and restructuring it entirely by hand. I then adjusted the `/review-plan` definition to address this challenge.

- When `/revise-plan` is invoked, it uses the suggested fix for each issue identified during the plan review. The fix usually meets the reviewer's criteria when `/review-plan` is called with an `approach` argument. However, sometimes the proposed solution does not meet the expected criteria, makes assumptions, or could trigger cascading issues. Providing additional context helps steer the suggested fix and revise the plan safely without requiring a manual review.

## Interesting Findings

- Enforcing the generation of high-level specifications in the plan format first, including the overview, architectural decisions, API endpoints, and frontend views, before proceeding with the increment definitions helps the LLM constrain the generation process and keep increments within the established boundaries, using the attention mechanism to reduce inconsistencies across the plan.

- A planner-reviewer does not need the context that the planner acquires while creating the plan to review and refine it correctly. Focusing on `TASK` as a reference to identify inconsistencies in definitions, increment dependencies, backend APIs, and their usage in the frontend guidelines can produce similar results with a more context-efficient approach.

- To change the vertical-slicing approach used in my initial plan, I defined an `approach` argument for `/review-plan`, instructing it to focus on establishing the core domain models and business rules in the initial increments. Once this foundation is in place, subsequent increments can continue using vertical slicing. This approach helps ensure that vertical slices are more likely to respect the dependencies established by the domain models and business rules.

- I believe these skills can produce good results even without guidance, as long as we review the output manually afterward. But building ways to guide them does more than save review time: it lets us hand off our focused judgment to a tool that's excellent at following instructions but struggles to adopt a particular perspective without spending a lot of tokens.