"""Write the run's context files from stdin, validating each entry before it lands.

Agents never read or rewrite the journal whole; an entry with an unknown type or a missing field is refused.

Usage:
  python .claude/scripts/context-write.py append PROGRESS.md <<'ENTRY'
  python .claude/scripts/context-write.py write CHANGELOG.md <<'ENTRY'
  python .claude/scripts/context-write.py write BLOCKED.md <<'ENTRY'
  python .claude/scripts/context-write.py delete BLOCKED.md

`{now}` in the text becomes the current `YYYY-MM-DD HH:MM`, so entries carry a real timestamp.
On append, a heading written with a date only (`## [YYYY-MM-DD] —`) or a placeholder gets the current time too,
so every journal entry has the same timestamp shape whoever wrote it.
A PROGRESS entry of an unknown type, or missing a field its type requires (PROGRESS_PATTERNS.md), is refused
with exit code 1 and the list of what is missing; nothing is written, so the agent fixes the entry and appends again.
"""
import re
import sys
from datetime import datetime
from pathlib import Path

CONTEXT = Path(__file__).resolve().parents[2] / "context"
ALLOWED = {
    "append": ("PROGRESS.md",),
    "write": ("CHANGELOG.md", "BLOCKED.md"),
    "delete": ("BLOCKED.md",),
}
# An entry heading whose timestamp is not `YYYY-MM-DD HH:MM`: a bare date or a placeholder like [now].
UNTIMED = re.compile(r"(?m)^## \[(?!\d{4}-\d{2}-\d{2} \d{2}:\d{2}\])[^\]]*\]")
HEADING = re.compile(r"## \[[^\]]*\] — (\w+) — .+")
COMMON = ("agent", "increment", "summary")
# Keep in step with the table in PROGRESS_PATTERNS.md.
REQUIRED = {
    "Increment": ("attempt", "files_created", "files_modified", "interfaces_created", "decisions", "deviations",
                  "tests", "blockers"),
    "CodeReview": ("attempt", "result", "critical_high", "medium_findings", "files_reviewed"),
    "TestRun": ("attempt", "mode", "result", "tests", "coverage", "failures"),
    "Validation": ("trigger", "rewritten"),
    "Escalation": ("diagnosis", "decision", "resume_at"),
    "Refactor": ("files_modified", "tests"),
    "Plan": ("status",),
    "Review": ("status",),
    "Fix": ("status", "root_cause", "solution", "regression_test", "tests", "blockers"),
}


def entry_problems(text):
    """Return what is wrong with each journal entry in the text; an empty list means it can be appended."""
    blocks = [block for block in re.split(r"(?m)^(?=## \[)", text) if block.startswith("## [")]
    if not blocks:
        return ["no entry heading `## [{now}] — <Type> — <Label>` found"]
    problems = []
    for block in blocks:
        heading = HEADING.match(block)
        if not heading or heading[1] not in REQUIRED:
            problems.append(f"unknown entry type in `{block.splitlines()[0]}`; use one of {', '.join(REQUIRED)}")
            continue
        fields = dict(re.findall(r"(?m)^- (\w+): *(.*)$", block))
        missing = [name for name in COMMON + REQUIRED[heading[1]] if not fields.get(name, "").strip()]
        if missing:
            problems.append(f"{heading[1]} entry is missing: {', '.join(missing)}")
    return problems


def main(argv):
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    if len(argv) != 2 or argv[1] not in ALLOWED.get(argv[0], ()):
        sys.exit(__doc__)
    action, target = argv[0], CONTEXT / argv[1]

    if action == "delete":
        target.unlink(missing_ok=True)
        print(f"{argv[1]} removed.")
        return 0

    now = f"{datetime.now():%Y-%m-%d %H:%M}"
    text = sys.stdin.read().replace("{now}", now).strip("\n") + "\n"
    if action == "append":
        text = UNTIMED.sub(f"## [{now}]", text)
        problems = entry_problems(text)
        if problems:
            print("PROGRESS.md: entry refused, nothing written.")
            print("\n".join(f"- {problem}" for problem in problems))
            print("See context/PROGRESS_PATTERNS.md, fix the entry and append it again.")
            return 1
        with target.open("a", encoding="utf-8", newline="\n") as file:
            file.write("\n" + text)
    else:
        target.write_text(text, encoding="utf-8", newline="\n")
    print(f"{argv[1]}: {action} done.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
