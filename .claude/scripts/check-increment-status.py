"""Check whether an increment of context/PLAN.md can be implemented, or list all statuses.

Usage:
  python .claude/scripts/check-increment-status.py <n>      (accepts 7 or I7)
  python .claude/scripts/check-increment-status.py --list

With <n>, the first line is `OK:` when the increment exists, is pending or blocked
(a blocked increment may be retried) and all its dependencies are completed (I0, the
existing skeleton, counts as completed), and `BLOCKED:` otherwise. Always exits 0 so command `!` injection shows the reason.
"""
import re
import sys
from pathlib import Path

PLAN = Path(__file__).resolve().parents[2] / "context" / "PLAN.md"
HEADING = re.compile(r"^### (I\d+) — (.*)$")
STATUS = re.compile(r"^- \*\*Status:\*\* `([a-z-]+)`")
DEPENDS = re.compile(r"^- \*\*Depends on:\*\* (.*)$")
SKELETON = "I0"
STARTABLE = ("pending", "blocked")


def parse(lines):
    """Return {id: {title, status, depends}} in plan order."""
    increments, current = {}, None
    for line in lines:
        if m := HEADING.match(line):
            current = {"id": m[1], "title": m[2], "status": None, "depends": []}
            increments[m[1]] = current
        elif current:
            if s := STATUS.match(line):
                current["status"] = s[1]
            elif d := DEPENDS.match(line):
                current["depends"] = re.findall(r"\bI\d+\b", d[1])
    return increments


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    increments = parse(PLAN.read_text(encoding="utf-8").splitlines())

    if argv == ["--list"]:
        for inc in increments.values():
            print(f"{inc['id']}\t{inc['status']}\t{','.join(inc['depends']) or '-'}\t{inc['title']}")
        return 0
    if len(argv) != 1 or not re.fullmatch(r"[Ii]?\d+", argv[0]):
        print(f"BLOCKED: invalid increment '{' '.join(argv)}'; use a number like 7 or I7.")
        return 0

    key = "I" + argv[0].lstrip("Ii")
    inc = increments.get(key)
    if inc is None:
        print(f"BLOCKED: {key} not found. Available: {', '.join(increments)}")
        return 0

    problems = []
    if inc["status"] not in STARTABLE:
        problems.append(f"{key} is '{inc['status']}', not 'pending' or 'blocked'.")
    for dep in inc["depends"]:
        status = "completed" if dep == SKELETON else increments.get(dep, {}).get("status")
        if status != "completed":
            problems.append(f"Dependency {dep} is '{status}', not 'completed'.")
    if problems:
        print("BLOCKED: " + " ".join(problems))
    else:
        print(f"OK: {key} — {inc['title']} is {inc['status']}; dependencies completed: {', '.join(inc['depends']) or 'none'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
