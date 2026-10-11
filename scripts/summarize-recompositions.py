#!/usr/bin/env python3
"""Summarize DebugRecomposeLogger logcat output; budgets apply to a supplied scenario window."""
import argparse
import collections
import json
import re
import sys
from pathlib import Path


def summarize(log):
    header = re.compile(r"\[Recomposition #(\d+)\].*?\(fq: ([^)]+)\)")
    parameter = re.compile(r"\[param\] ([^:]+): .*? (stable \(|unstable \(|changed \(|reference changed \()")
    state = re.compile(r"\[state\] ([^:]+): .*? changed \(")
    scopes = {}
    current = None
    for line in log.splitlines():
        match = header.search(line)
        if match:
            current = scopes.setdefault(match[2], {
                "events": 0, "first_counter": int(match[1]), "last_counter": 0,
                "changed_parameters": collections.Counter(),
                "reference_only_parameters": collections.Counter(),
                "changed_states": collections.Counter(),
            })
            current["events"] += 1
            current["last_counter"] = int(match[1])
        elif current is not None:
            match = parameter.search(line)
            if match and match[2].startswith(("changed", "reference")):
                key = "reference_only_parameters" if match[2].startswith("reference") else "changed_parameters"
                current[key][match[1]] += 1
            match = state.search(line)
            if match:
                current["changed_states"][match[1]] += 1
    return dict(sorted(scopes.items()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--max-events", action="append", default=[], metavar="FQ_NAME=COUNT",
                        help="Optional per-scope budget; exits 1 when exceeded. Count 0 requires no events.")
    args = parser.parse_args()
    budgets = {}
    for value in args.max_events:
        try:
            scope, limit = value.rsplit("=", 1)
            limit = int(limit)
            if not scope or limit < 0:
                raise ValueError()
            budgets[scope] = limit
        except ValueError:
            parser.error("--max-events must be FQ_NAME=nonnegative_integer")
    # Android logcat can truncate long model values in the middle of a UTF-8 character.
    scopes = summarize(args.log.read_text(encoding="utf-8", errors="replace"))
    exceeded = {scope: {"events": scopes.get(scope, {}).get("events", 0), "limit": limit}
                for scope, limit in budgets.items()
                if scopes.get(scope, {}).get("events", 0) > limit}
    print(json.dumps({"scopes": scopes, "budget_exceeded": exceeded}, ensure_ascii=False, indent=2))
    if not scopes:
        print("No matching events: check the trace-enabled build, logger and scenario window.", file=sys.stderr)
        return 2
    return 1 if exceeded else 0


if __name__ == "__main__":
    sys.exit(main())
