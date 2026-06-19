---
# isaac-7e60
title: 'modules show <name>: full detail for one module (brew info)'
status: completed
type: feature
priority: normal
tags:
created_at: 2026-06-19T18:43:30Z
updated_at: 2026-06-19T18:53:14Z
---

The compact `modules list` table omits full coords (you see "git isaac-acp@d108562",
not the url). `modules show <name>` is the drill-down (= brew info).

## Behavior

`isaac modules show <name>` — full block for ONE module: id, version, status,
FULL coordinate (git url + sha/tag, or :local/root), source (registry / local /
hand-pinned), and required-by (the modules that pull it). --edn/--json for
structured. Unknown id -> error, exit 1.

## Scenarios -> features/module/modules_show.feature (@wip)

1. show prints the full coordinate (git url + sha visible).
2. show --edn emits structured detail (id + coord.git/url).
3. show of an unknown module -> "Unknown module: <name>", exit 1.

## Notes

• required-by depends on isaac-90df (git-coord transitive discovery) to be
  meaningful for published modules; until then show it for what resolves.
• Keeps the list table compact; detail on demand. `modules list -v` (full coords
  inline) was the considered alternative — show chosen.

## Verification Notes

2026-06-19 verifier:

- Verified against fetched GitHub `isaac-foundation` `main` at `11a7fd9`.
- `env ISAAC_GIT=1 bb features-all features/module/modules_show.feature` passed: `3 examples, 0 failures, 7 assertions`.
- Full repo lane on the same head passed: `env ISAAC_GIT=1 bb ci` -> `754` spec examples, `0` failures; `109` feature examples, `0` failures.
