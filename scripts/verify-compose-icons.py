#!/usr/bin/env python3
"""Fail if an Android APK omits literal RemixIcon references from project Kotlin sources.

Run after assembleRelease (including incremental builds):
  python3 scripts/verify-compose-icons.py androidApp/build/outputs/apk/release/androidApp-release.apk
"""
import argparse
from pathlib import Path
import re
import zipfile

EXCLUDED = {"build", ".gradle", ".git", ".idea", ".claude", "node_modules", "midscene",
            "build-logic", "release-page", "docs", "kotlin-js-store"}
REFERENCE = re.compile(r"RemixIcon\.([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)")
PREFIX = "assets/composeResources/com.lalilu.remixicon.generated.resources/drawable/"


def snake_case(value):
    return re.sub(r"(?<!^)([A-Z0-9])", r"_\1", value).lower()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    references = set()
    for path in root.rglob("*.kt"):
        if EXCLUDED.intersection(path.relative_to(root).parts):
            continue
        references.update(f"{snake_case(category)}_{snake_case(name)}.xml"
                          for category, name in REFERENCE.findall(path.read_text(encoding="utf-8")))
    if not references:
        raise SystemExit("No icon references found; cannot verify APK")
    with zipfile.ZipFile(args.apk) as apk:
        packaged = {name.removeprefix(PREFIX) for name in apk.namelist() if name.startswith(PREFIX)}
    missing = sorted(references - packaged)
    if missing:
        raise SystemExit("APK is missing referenced Compose icons: " + ", ".join(missing))
    print(f"Verified {len(references)} referenced Compose icons in {args.apk}")


if __name__ == "__main__":
    main()
