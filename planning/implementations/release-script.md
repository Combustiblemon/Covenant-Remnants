# Release script — `release.bat vX.Y.Z` (2026-10-12)

One command for a release. Before this, a release meant bumping three version files by hand,
rolling the changelog, running `packageMod`, committing, pushing and making the GitHub release in
the browser. v0.1.2 was bumped in the files but never released, and the files drifted
(`build.gradle.kts` 0.1.2, the others 0.1.1). The script bumps everything from one argument.

## Rulings (developer 2026-10-12)

- GitHub access through the **`gh` CLI** (installed once with `winget install --id GitHub.cli`,
  then `gh auth login`).
- **`master` only, fail loudly** if any other branch is checked out. The script never switches
  or merges branches (CLAUDE.md: branches are the developer's).
- Build and **attach `Covenant Remnants.zip`** to the release.

## Files

- `release.bat` (repo root) — wrapper: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\release.ps1 %*`.
- `scripts/release.ps1` — the script, PowerShell 5.1 compatible. Not shipped (neither file is in
  `runtimeFiles`).

## What `release.bat v0.1.2 [-DryRun]` does

Pre-flight, nothing modified until all pass: argument matches `v?X.Y.Z`; branch is `master`;
working tree clean; local master not behind `origin/master` (after `git fetch`); `gh` on PATH and
authenticated; tag `vX.Y.Z` absent locally and on origin; new version greater than the one in
`mod_info.json`; `CHANGELOG.md` has a `## Unreleased` section with at least one bullet.

Edits (text replace, never a JSON round-trip, so comments and line endings survive):

| File | Pattern |
| --- | --- |
| `mod_info.json` | `"version": "..."` (not `gameVersion`) |
| `covenant-remnants.version` | `"major":N`, `"minor":N`, `"patch":N` (inline comment kept) |
| `build.gradle.kts` | `version = "..."` (jar manifest) |
| `gradle.properties` | `project.version=` (stale before; now follows) |
| `CHANGELOG.md` | `## Unreleased` heading becomes `## vX.Y.Z — YYYY-MM-DD` (real em dash, today) over the same body; no empty `## Unreleased` is left behind (developer 2026-10-12) — the next change adds the heading back |

The Unreleased body is also written to `build/release-notes-vX.Y.Z.md` (gitignored) and becomes
the release description.

Build: `gradlew.bat packageMod` (LemonLib first through the composite build). On failure the five
edited files are reverted with `git checkout --` and nothing is committed.

`-DryRun` stops here: prints the diff stat and the notes, leaves the edits for inspection, tells
you the `git checkout --` line to undo.

Publish: `git add` the five files, `git commit -m "Release vX.Y.Z"`, `git tag -a vX.Y.Z`,
`git push origin master`, `git push origin vX.Y.Z`,
`gh release create vX.Y.Z "Covenant Remnants.zip#Covenant Remnants vX.Y.Z" --title vX.Y.Z --notes-file build/release-notes-vX.Y.Z.md`,
then prints the release URL. A failure after the commit is not rolled back; the message prints
the exact commands to resume (push / release create). Rerunning hits the "tag exists" check.

## Checks done

- Script parses (PSParser, 0 errors). No-arg and bad-arg runs print usage, exit 1.
- Run on `develop`: "current branch is 'develop'. Releases are made from 'master' only", exit 1.
- Changelog roll tested against the real `CHANGELOG.md` (two subheadings under Unreleased): the
  notes are captured whole, the new heading replaces `## Unreleased`, the old `## v0.1.1`
  section keeps its blank line. A second run on the rolled file aborts with "no '## Unreleased'
  section".
- Not run: a real release, `-DryRun` on master (needs the tree committed and master checked out).

## LemonLib (developer 2026-10-12: "we need the same for LemonLib")

Same script, parameterised: `scripts/release.ps1` now starts with a config block (`$ModName`,
`$VersionFileName`, `$ZipName`, `$ReleaseBranch`) and the body is identical in both repos.
LemonLib (`../LemonLib`, branch `main`, `lemonlib.version`, `LemonLib.zip`) got `release.bat`,
`scripts/release.ps1`, a new `CHANGELOG.md` (ruling: create one; `v0.1.0 — 2026-10-10` for the
first packaged release, `## Unreleased` with today's `PolygonSmoothing` additions), `CHANGELOG.md`
added to `releaseFiles` in its `build.gradle.kts`, and a README "Releasing" paragraph. When the
script body changes, copy it to the other repo and re-apply the four config lines.

## Open items

- First real use: install `gh`, `gh auth login`, then `release.bat v0.1.2 -DryRun` on master
  before the real run.
- `covenant-remnants.version` has no `directDownloadURL` / `changelogURL`; the release asset URL
  could fill them (design call, not done).
- `planning/TODO.md` "Version Checker master file" item is satisfied by releasing from master.
