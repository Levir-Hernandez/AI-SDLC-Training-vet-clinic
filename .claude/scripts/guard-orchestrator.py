"""PreToolUse hook for the coordinating agents: each may run only its own short list of shell commands.

Usage (stdin is the hook JSON):
  python .claude/scripts/guard-orchestrator.py                 the orchestrator: read-only harness scripts
  python .claude/scripts/guard-orchestrator.py git-manager     the git-manager: git add -A, git commit -m, git log only

Every call must be exactly one allowed command. `&&`, `;`, pipes, redirects, backticks, `$(...)` and `cd`
are refused. Only the body of a heredoc and the text of a `-m "..."` message are not checked.
Exit code 2 blocks the tool call and shows stderr to the agent.
"""
import json
import re
import sys

SCRIPT = r"python \.claude/scripts/"
ROLES = {
    # The orchestrator reads state and delegates; every write goes through an agent or /escalate.
    "orchestrator": [
        SCRIPT + r"(check-increment-status|check-plan-review-status|read-increment|read-progress|run-cost)\.py( [\w .:-]*)?",
        SCRIPT + r"context-write\.py (write|delete) BLOCKED\.md",
        SCRIPT + r"update-increment-status\.py [\w-]+ completed",
    ],
    "git-manager": [
        r"git add -A",
        r'git commit -m "[^"]*"',
        r"git log --oneline( -\d+)?",
    ],
}
FORBIDDEN = re.compile(r"&&|\|\||[;|<>`]|\$\(|\n")
HEREDOC = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?\n.*?\n\1\s*$", re.DOTALL)


def strip_text(command):
    """Remove a trailing heredoc body; the command text before it is what gets checked."""
    return HEREDOC.sub("", command).rstrip()


def allowed(role, command):
    checked = strip_text(command)
    # A commit or stash message may say anything; check the command with an empty message in its place.
    shape = re.sub(r'-m "[^"]*"', '-m ""', checked)
    if FORBIDDEN.search(re.sub(r'-m ""', "", shape)):
        return False
    return any(re.fullmatch(pattern, checked) for pattern in ROLES[role])


def main(argv):
    role = argv[0] if argv else "orchestrator"
    payload = json.load(sys.stdin)
    command = payload.get("tool_input", {}).get("command", "").strip()
    # The orchestrator's hook also fires for the subagents it launches; only police the agent this hook belongs to.
    caller = payload.get("agent_type")
    if caller and caller != role:
        return 0
    if role in ROLES and allowed(role, command):
        return 0
    print(
        f"The {role} may not run: {command.splitlines()[0][:120] if command else '(empty)'}\n"
        "Run exactly one allowed command per call, with no `cd`, `&&`, `;`, pipes or redirects. "
        + ("Allowed: the read-only harness scripts (check-*, read-*, run-cost), update-increment-status <id> completed, and BLOCKED.md through context-write. "
           "Delegate everything else: code to implementation, commits to git-manager."
           if role == "orchestrator" else
           "Allowed: git add -A, git commit -m \"...\", git log --oneline."
           ),
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
