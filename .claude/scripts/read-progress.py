"""Read context/PROGRESS.md without loading all of it, and answer the orchestrator's counters.

Usage:
  python .claude/scripts/read-progress.py --last <n>            last n entries (0 = every entry)
  python .claude/scripts/read-progress.py --attempt <id>        the attempt number the next try of an increment gets
  python .claude/scripts/read-progress.py --attempts <id>       failed gates since its last escalation
  python .claude/scripts/read-progress.py --escalations <id>    escalations of the increment and its splits
  python .claude/scripts/read-progress.py --drift               pre-flight: does planner Mode 2 run before the next increment
  python .claude/scripts/read-progress.py --since-validation    increments passed since the last validation
  python .claude/scripts/read-progress.py --repeated-findings   Medium findings on the last 2 passed increments
  python .claude/scripts/read-progress.py --findings <id>       Medium findings of the earlier attempts of an increment
  python .claude/scripts/read-progress.py --finding-keys        every Medium finding key used so far
  python .claude/scripts/read-progress.py --since-changelog     entries not yet in CHANGELOG.md, after its marker line
  python .claude/scripts/read-progress.py --stats               run totals for the Workflow Complete message

Each agent writes its own entry. An attempt's gate is worked out here: it passes when the attempt's `CodeReview`
and `TestRun` entries both say PASS, and fails when either says FAIL (ERROR is no gate). Old journals with
`Gate` entries still count.
"""
import re
import sys
from pathlib import Path

PROGRESS = Path(__file__).resolve().parents[2] / "context" / "PROGRESS.md"
CHANGELOG = PROGRESS.with_name("CHANGELOG.md")
MARKER = re.compile(r"<!-- progress-entries: (\d+) -->")
TITLE = re.compile(r"^## \[[^\]]*\] — (\w+) — ")
INCREMENT_ID = re.compile(r"I\d+[a-z]?")
RETRY_WARNING = 40
VALIDATION_CADENCE = 3
OPTIONS = ("--last", "--attempt", "--attempts", "--escalations", "--drift", "--since-validation",
           "--repeated-findings", "--findings", "--finding-keys", "--since-changelog", "--stats")


def entries():
    """Return [{type, text, fields}] for every entry; entries outside the patterns have type None."""
    if not PROGRESS.exists():
        return []
    result = []
    for block in re.split(r"(?m)^(?=## )", PROGRESS.read_text(encoding="utf-8")):
        if block.startswith("## "):
            match = TITLE.match(block)
            fields = dict(re.findall(r"(?m)^- (\w+): (.*)$", block))
            result.append({"type": match[1] if match else None, "text": block.rstrip(), "fields": fields})
    return result


def timeline(all_entries):
    """The entries in order, with a Gate pseudo-entry right after the review or test entry that decides an attempt."""
    result, pending, decided = [], {}, set()
    for entry in all_entries:
        result.append(entry)
        fields = entry["fields"]
        if entry["type"] not in ("CodeReview", "TestRun") or not INCREMENT_ID.fullmatch(fields.get("increment", "")):
            continue
        key = (fields["increment"], fields.get("attempt", "?"))
        if key in decided:
            continue
        pending.setdefault(key, {})[entry["type"]] = fields
        review, test = pending[key].get("CodeReview", {}), pending[key].get("TestRun", {})
        results = (review.get("result", "").upper(), test.get("result", "").upper())
        if "FAIL" in results or results == ("PASS", "PASS"):
            decided.add(key)
            result.append({"type": "Gate", "text": "", "fields": {
                "increment": key[0], "attempt": key[1],
                "result": "PASS" if results == ("PASS", "PASS") else "FAIL",
                "medium_findings": review.get("medium_findings", "none"),
            }})
    return result


def normalize(value):
    return "I" + value.lstrip("Ii")


def root(increment):
    return re.sub(r"[a-z]$", "", increment)


def attempts(events, key):
    count = 0
    for entry in events:
        if entry["fields"].get("increment") != key:
            continue
        if entry["type"] == "Escalation":
            count = 0
        elif entry["type"] == "Gate" and entry["fields"].get("result") == "FAIL":
            count += 1
    return count


def gates(events, key=None):
    return [e for e in events if e["type"] == "Gate" and (key is None or e["fields"].get("increment") == key)]


def passed(events):
    return [e for e in gates(events) if e["fields"].get("result") == "PASS"]


def finding_keys(entry):
    return set(re.findall(r"[\w-]+", entry["fields"].get("medium_findings", ""))) - {"none"}


def since_validation(events):
    """The events after the last Validation entry."""
    last = max((i for i, e in enumerate(events) if e["type"] == "Validation"), default=-1)
    return events[last + 1:]


def drift(events):
    """Mode 2 runs before the next increment when deviations were reported, or every VALIDATION_CADENCE increments."""
    recent = since_validation(events)
    done = {e["fields"]["increment"] for e in passed(recent)}
    deviations = [
        (e["fields"]["increment"], e["fields"].get("deviations", "").strip())
        for e in recent
        if e["type"] == "Increment" and e["fields"].get("increment") in done
    ]
    deviations = [(inc, text) for inc, text in deviations if text and not text.lower().startswith("none")]
    if deviations:
        print("DRIFT: deviations")
        print("\n".join(f"- {inc}: {text}" for inc, text in deviations))
    elif len(done) >= VALIDATION_CADENCE:
        print(f"DRIFT: cadence ({len(done)} increments passed since the last validation)")
    else:
        print("DRIFT: none")


def since_changelog(all_entries):
    """Print the marker to keep in CHANGELOG.md, then only the entries it does not reflect yet."""
    marker = MARKER.search(CHANGELOG.read_text(encoding="utf-8")) if CHANGELOG.exists() else None
    done = min(int(marker[1]), len(all_entries)) if marker else 0
    print(f"<!-- progress-entries: {len(all_entries)} -->")
    print("\n\n".join(e["text"] for e in all_entries[done:]) or "No new entries.")


def stats(events):
    done = passed(events)
    retried = {e["fields"].get("increment") for e in gates(events) if e["fields"].get("result") == "FAIL"}
    rate = round(100 * len(retried) / len(done)) if done else 0
    print(f"increments_passed: {len(done)}")
    print(f"autonomous_first_try: {sum(e['fields'].get('increment') not in retried for e in done)}")
    print(f"required_retries: {len(retried)}")
    print(f"mode3_escalations: {sum(e['type'] == 'Escalation' for e in events)}")
    print(f"forward_validations: {sum(e['type'] == 'Validation' for e in events)}")
    print(f"refactors: {sum(e['type'] == 'Refactor' for e in events)}")
    print(f"retry_rate: {rate}%")
    if rate > RETRY_WARNING:
        print(f"WARNING: more than {RETRY_WARNING}% of increments required retries; "
              "the planner is producing increments that are too large.")


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if not argv or argv[0] not in OPTIONS:
        sys.exit(__doc__)
    all_entries = entries()
    events = timeline(all_entries)
    option = argv[0]

    if option == "--last":
        count = int(argv[1])
        print("\n\n".join(e["text"] for e in (all_entries[-count:] if count else all_entries)) or "No entries yet.")
    elif option == "--attempt":
        key = normalize(argv[1])
        print(len(gates(events, key)) + 1)
    elif option == "--attempts":
        print(attempts(events, normalize(argv[1])))
    elif option == "--escalations":
        key = root(normalize(argv[1]))
        print(sum(e["type"] == "Escalation" and root(e["fields"].get("increment", "")) == key for e in events))
    elif option == "--drift":
        drift(events)
    elif option == "--since-validation":
        print(len(passed(since_validation(events))))
    elif option == "--repeated-findings":
        last_two = passed(events)[-2:]
        repeated = sorted(finding_keys(last_two[0]) & finding_keys(last_two[1])) if len(last_two) == 2 else []
        print(", ".join(repeated) or "none")
    elif option == "--findings":
        keys = set().union(*(finding_keys(e) for e in gates(events, normalize(argv[1]))))
        print(", ".join(sorted(keys)) or "none")
    elif option == "--finding-keys":
        print(", ".join(sorted(set().union(*(finding_keys(e) for e in gates(events))))) or "none")
    elif option == "--since-changelog":
        since_changelog(all_entries)
    else:
        stats(events)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
