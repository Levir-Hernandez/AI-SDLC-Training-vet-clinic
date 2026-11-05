"""Print per-agent tokens and an estimated cost of a run from its Claude Code session transcripts. Read-only.

Usage:
  python .claude/scripts/run-cost.py                  the latest session of this project
  python .claude/scripts/run-cost.py <session-id>     one session (the id of ~/.claude/projects/<project>/<id>.jsonl)
  python .claude/scripts/run-cost.py --all            every session of this project
  python .claude/scripts/run-cost.py --dir <path>     the transcripts of another project folder in ~/.claude/projects

The cost is an estimate at API list prices; on a subscription it is only what the usage would cost at API rates.
A transcript that cannot be read is skipped with a WARN line; the script never fails a run.
"""
import json
import re
import sys
from pathlib import Path

# Per million tokens, Claude API list prices. Source: claude-api skill model table, cached 2026-10-06.
# Cache writes: 1.25x input for the 5-minute TTL, 2x input for the 1-hour TTL. Update this table and its date together.
PRICES_DATE = "2026-10-06"
PRICES = {
    #  model               input  output  cache read
    "claude-fable-5-1":  (10.00, 50.00, 0.25),
    "claude-opus-5-5":   (4.00, 20.00, 0.20),
    "claude-sonnet-5-5": (2.00, 10.00, 0.20),
    "claude-haiku-5-5":  (0.10, 0.50, 0.01),
}
WRITE_5M, WRITE_1H = 1.25, 2.00
# Haiku 5.5 bills prompts over 100K tokens at $0.50 / $2.50; cache prices are assumed to scale the same way (x5).
LONG_PROMPT = {"claude-haiku-5-5": (100_000, 5.0)}

ROOT = Path(__file__).resolve().parents[2]
PROJECTS = Path.home() / ".claude" / "projects"
FIELDS = ("input", "cache_write", "cache_read", "output")


def long_path(path):
    """Windows cannot open paths of 260 characters or more without the extended-length prefix."""
    path = Path(path).resolve()
    return Path("\\\\?\\" + str(path)) if sys.platform == "win32" and len(str(path)) >= 260 else path


def project_dir(path=ROOT):
    """Claude Code stores a project's sessions under its path with every non-alphanumeric character as '-'."""
    return PROJECTS / re.sub(r"[^A-Za-z0-9]", "-", str(Path(path).resolve()))


def message_cost(model, usage):
    rates = PRICES.get(model)
    if rates is None:
        return None
    input_rate, output_rate, read_rate = rates
    prompt = sum(usage.get(k) or 0 for k in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"))
    limit, factor = LONG_PROMPT.get(model, (None, 1.0))
    if limit is None or prompt <= limit:
        factor = 1.0
    split = usage.get("cache_creation") or {}
    write_1h = split.get("ephemeral_1h_input_tokens") or 0
    write_5m = (usage.get("cache_creation_input_tokens") or 0) - write_1h
    dollars = ((usage.get("input_tokens") or 0) * input_rate
               + write_5m * input_rate * WRITE_5M + write_1h * input_rate * WRITE_1H
               + (usage.get("cache_read_input_tokens") or 0) * read_rate
               + (usage.get("output_tokens") or 0) * output_rate)
    return dollars * factor / 1_000_000


def agent_name(transcript):
    meta = transcript.with_name(transcript.name[: -len(".jsonl")] + ".meta.json")
    try:
        return json.loads(long_path(meta).read_text(encoding="utf-8")).get("agentType") or "subagent"
    except (OSError, ValueError):
        return "main" if transcript.parent.name != "subagents" else "subagent"


def read_usage(transcript, warnings):
    """Return {message id: (model, usage)}; streamed messages repeat their usage, so the last copy wins."""
    try:
        lines = long_path(transcript).read_text(encoding="utf-8").splitlines()
    except (OSError, ValueError) as error:
        warnings.append(f"WARN: skipped unreadable transcript {transcript.name}: {error}")
        return {}
    usage = {}
    for number, line in enumerate(lines):
        try:
            message = json.loads(line).get("message") or {}
        except (ValueError, AttributeError):
            continue
        if isinstance(message, dict) and message.get("usage") and message.get("model") != "<synthetic>":
            usage[message.get("id") or f"line-{number}"] = (message.get("model", "unknown"), message["usage"])
    return usage


def sessions(argv):
    folder = project_dir()
    if argv[:1] == ["--dir"] and len(argv) > 1:
        folder, argv = Path(argv[1]).expanduser(), argv[2:]
    if not folder.is_dir():
        return folder, []
    found = sorted(folder.glob("*.jsonl"), key=lambda p: p.stat().st_mtime)
    if argv[:1] == ["--all"]:
        return folder, found
    if argv:
        return folder, [p for p in found if p.stem == argv[0]]
    return folder, found[-1:]


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if argv[:1] in (["-h"], ["--help"]):
        print(__doc__)
        return 0
    folder, chosen = sessions(argv)
    if not chosen:
        print(f"WARN: no session transcripts found in {folder}.")
        return 0

    warnings, totals = [], {}
    for session in chosen:
        files = [session, *sorted((session.parent / session.stem / "subagents").glob("*.jsonl"))]
        for transcript in files:
            agent = "main" if transcript == session else agent_name(transcript)
            for model, usage in read_usage(transcript, warnings).values():
                row = totals.setdefault((agent, model), dict.fromkeys(FIELDS, 0) | {"cost": 0.0, "priced": True})
                row["input"] += usage.get("input_tokens") or 0
                row["cache_write"] += usage.get("cache_creation_input_tokens") or 0
                row["cache_read"] += usage.get("cache_read_input_tokens") or 0
                row["output"] += usage.get("output_tokens") or 0
                cost = message_cost(model, usage)
                if cost is None:
                    row["priced"] = False
                else:
                    row["cost"] += cost

    print(f"Sessions: {', '.join(s.stem for s in chosen)}")
    print(f"Prices: API list prices dated {PRICES_DATE}, 5-minute cache writes at {WRITE_5M}x input, 1-hour at {WRITE_1H}x.\n")
    print("| Agent | Model | Input | Cache write | Cache read | Output | Est. cost |")
    print("|---|---|---|---|---|---|---|")
    grand = dict.fromkeys(FIELDS, 0) | {"cost": 0.0}
    for (agent, model), row in sorted(totals.items(), key=lambda item: -item[1]["cost"]):
        cost = f"${row['cost']:.2f}" if row["priced"] else "unpriced"
        print(f"| `{agent}` | {model} | " + " | ".join(f"{row[f]:,}" for f in FIELDS) + f" | {cost} |")
        if not row["priced"]:
            warnings.append(f"WARN: no price for model {model}; add it to PRICES to include it in the total.")
        for field in grand:
            grand[field] += row[field]
    print("| **Total** | | " + " | ".join(f"{grand[f]:,}" for f in FIELDS) + f" | **${grand['cost']:.2f}** |")
    for warning in dict.fromkeys(warnings):
        print(warning)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
