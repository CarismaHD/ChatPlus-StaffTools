"""Release check: makes sure no private term reaches the public repo, its history, or the jar.

The list of terms is yours and stays outside this repository (one term per line, # for comments). Put in it
anything that must not be published: server addresses, server command names, staff and player names, your real
name and email, local paths.

    python tools/check-private-terms.py <private-terms.txt> [--base <upstream-commit>] [--jar <built jar>]

It checks, case-insensitively:
  * every file tracked in the current commit,
  * every line added since --base (default: ce581e6, ChatPlus 2.7.0), in every commit, so history is covered too,
  * every commit message, author and committer name and email since --base,
  * every file in the jar, including the text inside class files (bundled libraries in META-INF/jars are skipped).

Exits with 1 and lists each hit when anything is found, 0 when clean.
"""
import argparse
import re
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ap = argparse.ArgumentParser()
ap.add_argument("terms")
ap.add_argument("--base", default="ce581e6")
ap.add_argument("--jar")
args = ap.parse_args()

with open(args.terms, encoding="utf-8") as f:
    terms = [t.rstrip("\r\n") for t in f if t.strip() and not t.lstrip().startswith("#")]
if not terms:
    sys.exit("The terms file is empty.")
pattern = re.compile("|".join(re.escape(t) for t in terms), re.IGNORECASE)


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, encoding="utf-8", errors="replace", check=True).stdout


hits = []
for path in git("ls-files").splitlines():
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        continue
    for n, line in enumerate(text.splitlines(), 1):
        if pattern.search(line):
            hits.append(f"file {path}:{n}: {line.strip()[:160]}")

commit, path = "", ""
for line in git("log", "-p", "--no-color", "--format=@@COMMIT %h", f"{args.base}..HEAD").splitlines():
    if line.startswith("@@COMMIT "):
        commit = line.split()[1]
    elif line.startswith("+++ "):
        path = line[6:]
    elif line.startswith("+") and pattern.search(line):
        hits.append(f"history {commit} {path}: {line[1:].strip()[:160]}")

for line in git("log", "--format=%h %an <%ae> / %cn <%ce>%n%B", f"{args.base}..HEAD").splitlines():
    if pattern.search(line):
        hits.append(f"commit info: {line.strip()[:160]}")

if args.jar:
    with zipfile.ZipFile(args.jar) as z:
        for name in z.namelist():
            if name.startswith("META-INF/jars/"):
                continue
            if pattern.search(name):
                hits.append(f"jar entry name: {name}")
            data = z.read(name).decode("latin-1")
            for m in pattern.finditer(data):
                hits.append(f"jar {name}: ...{data[max(0, m.start() - 30):m.end() + 30]!r}...")

for h in hits:
    print(h)
print(f"{len(hits)} hit(s) for {len(terms)} term(s).")
sys.exit(1 if hits else 0)
