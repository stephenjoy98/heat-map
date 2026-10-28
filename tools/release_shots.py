"""Before-and-after pictures for the release notes: a Markdown section with every picture in
docs/screenshots/<x.y.z>/ for the releases the notes cover (newer than SINCE, up to and including VERSION).

    python3 -I tools/release_shots.py DIR VERSION [SINCE] SHA REPO

Pictures are named for what changed (e.g. widget-today.png becomes "Widget today") and linked at commit SHA so
they never change under a published release. Prints nothing when there are none. The app's update sheet stops
reading notes at the first "---", so this goes after it and only shows on GitHub.
"""
import os
import re
import sys


def key(v):
    return tuple(int(p) for p in v.split("."))


def main():
    root, version, since, sha, repo = sys.argv[1:6]
    since = since if re.fullmatch(r"\d+\.\d+\.\d+", since or "") else None
    if not os.path.isdir(root):
        return
    versions = sorted(
        (v for v in os.listdir(root) if re.fullmatch(r"\d+\.\d+\.\d+", v)
         and key(v) <= key(version) and (key(v) > key(since) if since else v == version)),
        key=key,
        reverse=True,
    )
    out = []
    for v in versions:
        pics = sorted(f for f in os.listdir(os.path.join(root, v)) if f.lower().endswith((".png", ".jpg", ".webp")))
        for f in pics:
            name = os.path.splitext(f)[0].replace("-", " ").replace("_", " ").strip().capitalize()
            label = f"{name} ({v})" if len(versions) > 1 else name
            out.append(f"**{label}**\n\n![{label}](https://raw.githubusercontent.com/{repo}/{sha}/{root}/{v}/{f})")
    if out:
        print("\n### Before and after\n\n" + "\n\n".join(out))


main()
