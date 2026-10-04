#!/usr/bin/env python3
"""Read-only guard against attaching user content or identifiers to Crashlytics."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
sources = list((root / "app/src/main").rglob("*.kt")) + list((root / "app/src/main").rglob("*.java"))
forbidden = re.compile(r"\b(setUserId|setCustomKey|setCustomKeys|recordException)\s*\(|FirebaseCrashlytics\.getInstance\(\)\.log\s*\(")
violations = []
for path in sources:
    for line_number, line in enumerate(path.read_text(errors="ignore").splitlines(), 1):
        if forbidden.search(line):
            violations.append(f"{path.relative_to(root)}:{line_number}")
if violations:
    raise SystemExit("Crashlytics custom user/content logging found: " + ", ".join(violations))
print("Crashlytics privacy guard passed: no custom user IDs, keys, exceptions, or custom logs in app source.")
