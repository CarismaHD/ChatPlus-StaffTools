# Releasing

Follow these steps for every public release on GitHub or CurseForge. The private-terms check is what keeps server details and personal details out of the release, so never skip it.

## 1. Keep private files out

These must never be committed or uploaded. Keep them in a folder outside this repository:

- Real server profiles and samples files.
- Your own ChatPlus settings (`chatplus-v*.json`).
- Your private terms list (step 2).
- Logs, screenshots and `run-*` folders from testing.

`.gitignore` also ignores `private/`, `*.private.json` and `*.private.txt`, in case one ends up in the folder by accident.

## 2. Keep a private terms list

Make a text file **outside this repository** with one term per line, and `#` for comments. Include everything that must never be published:

- Server addresses and names.
- Server command names and distinctive message text.
- Staff and player names.
- Your real name, email and local paths.

Never commit this file. It contains exactly what you are trying to hide.

## 3. Set the commit author

Use your GitHub noreply address, so your real email is never published:

```bash
git config user.name "<GitHub username>"
git config user.email "<id>+<GitHub username>@users.noreply.github.com"
```

Also turn on "Keep my email addresses private" and "Block command line pushes that expose my email" in GitHub's email settings.

## 4. Build and test

```bash
./gradlew :common:test :neoforge:remapJar
```

Before releasing, also run `./gradlew :neoforge:runSelfTest` and look at the screenshots in `neoforge/run-selftest/screenshots/`.

## 5. Run the private-terms check

```bash
python tools/check-private-terms.py /path/outside/repo/private-terms.txt --jar neoforge/build/libs/chatplus-stafftools-neoforge-<version>.jar
```

It must end with `0 hit(s)`. It checks:

- Every tracked file.
- Every line added since ChatPlus 2.7.0, in every commit.
- Every commit message, author and email.
- Everything inside the jar.

If something is found in history rather than in a current file, it is still published when you push, so the history has to be rewritten before pushing.

## 6. Publish

**GitHub.** Push only the release branch to your own repository, for example `ChatPlus-StaffTools`. Upstream ChatPlus history is already public and is fine to include.

**CurseForge.** ChatPlus is GPL-3.0, which allows publishing a changed version as long as you:

- Keep GPL-3.0 as the project license on CurseForge.
- Name a source link that points to your public GitHub repository.
- Credit ebicep and link the original ChatPlus clearly in the description.
- Give the project its own name ("ChatPlus Staff Tools") and say plainly that it is an unofficial fork.
- Set the project relations:
  - Required dependencies: Architectury API, Cloth Config API and Kotlin for Forge.
  - Incompatible: ChatPlus, since both use the mod ID `chatplus`.
- Upload only the built jar. Don't bundle a profile, settings or logs.

Asking ebicep first is not required by the license, but it is a courtesy that avoids surprises.

## 7. After publishing

Download the jar from CurseForge and run the private-terms check on it with `--jar`, to confirm the file people actually get is clean.
