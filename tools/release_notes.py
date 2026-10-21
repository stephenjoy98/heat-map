"""Release notes from CHANGELOG.md for one release: every entry newer than SINCE, up to and including VERSION.

    python3 -I tools/release_notes.py CHANGELOG.md VERSION [SINCE]

With no SINCE (or an unknown one), only VERSION's entry. Each entry is headed "## x.y.z" when there are several.
"""
import re
import sys


def key(v):
    return tuple(int(p) for p in v.split("."))


def main():
    path, version = sys.argv[1], sys.argv[2]
    since = sys.argv[3] if len(sys.argv) > 3 and re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[3] or "") else None
    entries = {}
    current = None
    for line in open(path, encoding="utf-8"):
        m = re.match(r"## \[(\d+\.\d+\.\d+)\]", line)
        if m:
            current = m.group(1)
            entries[current] = []
        elif line.startswith("## "):
            current = None
        elif current:
            entries[current].append(line.rstrip("\n"))
    wanted = sorted(
        (v for v in entries if key(v) <= key(version) and (since is None and v == version or since and key(v) > key(since))),
        key=key,
        reverse=True,
    )
    out = []
    for v in wanted:
        body = "\n".join(entries[v]).strip()
        out.append(f"## {v}\n\n{body}" if len(wanted) > 1 else body)
    print("\n\n".join(out))


main()
