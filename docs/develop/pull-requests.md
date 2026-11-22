# Pull requests

For contributors opening a pull request. CI checks the title and description
described here; the [template](../../.github/pull_request_template.md) fills in
the structure for you.

## Scope

- **One feature per PR.** A reviewer should understand it in a few minutes.
  Split unrelated fixes, refactors and renames into their own PRs.
- **Open a draft early** and push regularly, so CI runs and the direction can
  be checked before the work is finished. Mark it ready when it's done.
- **Stack dependent work.** When a PR builds on another open PR, base it on that
  branch and add `Stacked on #N.` to the description.

## Title

Use a [Conventional Commit](https://www.conventionalcommits.org/) title:
`type: summary`, for example `feat: add budget privacy mode` or
`fix(sync): keep pending changes after sign-in`. Don't add a `(#N)` suffix;
GitHub adds the number to the squash commit. Commits on the branch follow the
same `type: summary` form.

Use `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `ci`, `build` or
`chore`, matching the type of change ticked in the template.

## Description

Two sections are required:

- **`## Summary`**: a short paragraph on what changed and why, then bold-led
  bullets for the details.
- **`## Testing`**: concrete bullets on how you checked it.

The template also has `## Type of change`, `## Screenshots` and
`## Checklist`. Add `### Bugs found while testing` under Summary when testing
turned up bugs you fixed. Use a table only for a real mapping, such as error to
action.

```md
## Summary

Sync Center now offers the step that fixes a failed sync instead of a retry
that fails the same way.

- **Sign in again** for expired or revoked sign-ins.
- **Check server address** when the address doesn't reach Tandem.

## Testing

- On a Fold emulator with the device revoked, Sync Center showed **Sign in
  again**, and the right password led back to "Synced just now".
- `SyncRecoveryTest` covers the error-to-action mapping.
- ktlint, Android host tests and the debug build
```

## Testing

Say what you ran and what it showed, not just that tests pass:

- the device or emulator scenario and the result you saw;
- the test classes you added or changed;
- the checks you ran, such as ktlint, host tests and the debug build.

See [Test Tandem](testing.md) for the commands.

## Screenshots

For UI changes, add before and after screenshots under `## Screenshots`. Use a
short recording for motion or transitions. Delete the section for changes with
no UI.

## Code and attribution

- **Keep code comments minimal.** Put the reasoning in the commit message or PR
  description instead.
- **No tool attribution.** Don't add `Co-Authored-By` trailers or "Generated
  with" footers to commits or descriptions. The author is responsible for the
  whole change and should be able to explain every line.

## What CI checks

The `Conventional PR title` job runs `scripts/check-pr-title.py` and
`scripts/check-pr-body.py` on every pull request, including when the title or
description is edited. The description check fails when:

- `## Summary` or `## Testing` is missing, empty or still holds the template
  hints;
- it contains attribution, such as a "Generated with" footer, a
  `Co-Authored-By` trailer or an assistant's name.

Run it on a saved description with `python3 scripts/check-pr-body.py body.md`,
or on an existing PR:

```sh
gh pr view 60 --json body -q .body | python3 scripts/check-pr-body.py -
```
