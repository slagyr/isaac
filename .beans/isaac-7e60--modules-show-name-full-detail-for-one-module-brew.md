---
# isaac-7e60
title: 'modules show <name>: full detail for one module (brew info)'
status: todo
type: feature
created_at: 2026-06-19T18:43:30Z
updated_at: 2026-06-19T18:43:30Z
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
