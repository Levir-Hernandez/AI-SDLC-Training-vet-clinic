"""PreToolUse hook for the implementation agent: block direct access to the plan context files.

context/PLAN.md and context/PLAN_REVIEW.md are reached only through the plan scripts.
Exit code 2 blocks the tool call and shows stderr to the agent.
"""
import json
import sys

PROTECTED = ("PLAN.md", "PLAN_REVIEW.md")
ALLOWED = (
    "read-increment.py",
    "update-increment-status.py",
    "check-increment-status.py",
    "check-plan-review-status.py",
)

payload = json.load(sys.stdin)
tool_input = json.dumps(payload.get("tool_input", {}))

if any(name in tool_input for name in PROTECTED) and not any(script in tool_input for script in ALLOWED):
    print(
        "Direct access to context/PLAN.md and context/PLAN_REVIEW.md is blocked. Use the plan scripts:\n"
        "  python .claude/scripts/read-increment.py <n>                 read your increment\n"
        "  python .claude/scripts/check-increment-status.py <n>         pending + dependencies check\n"
        "  python .claude/scripts/check-plan-review-status.py <n>       verdict + findings for <n>\n"
        "  python .claude/scripts/update-increment-status.py <n> completed   mark it done",
        file=sys.stderr,
    )
    sys.exit(2)
sys.exit(0)
