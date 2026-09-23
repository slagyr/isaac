---
# isaac-6oly
title: Migrate hail bands to :_base and delete hail's local templating copy
status: todo
type: task
priority: normal
created_at: 2026-09-23T19:41:39Z
updated_at: 2026-09-23T19:41:39Z
---

Repo: **isaac-hail** (`src/isaac/hail/band_resolve.clj` — the local copy),
plus the orchestration trees and the live hosts.

Follows isaac-h2ck, which generalised templating into foundation
(`main-sha isaac-foundation 7de29b9`) but deliberately left hail's own copy in
place.

## Why

isaac-h2ck moved templating into foundation: `:_base` names a template
explicitly, `_<name>` entries are never addressable, one-level merge. Hail's own
implementation is now redundant — `template-band?`, `merge-bands` and the `base`
field in `band_resolve.clj` do the same job for bands alone.

The two coexist safely today, which is why h2ck did not force the migration:
hail's `apply-to-load-result!` runs earlier in the load and leaves behind no
`:base` field and no `_`-prefixed bands, so foundation's `resolve-config` is a
no-op on `:hail`. Nothing is broken. But there are two implementations of one
idea, and bands are the only config in Isaac that cannot use the general one.

## Change

- Bands migrate from `base:` to `:_base`.
- `band_resolve.clj`'s template handling is deleted; hail keeps only what is
  genuinely band-specific. Once bands use `:_base`, foundation needs no further
  change — `:hail` is an ordinary top-level table to the new code.

## Migration — two hosts and two repos

The band files are **not** all version-controlled in one place, and this is the
part that needs care:

- `slagyr/orchestration` → `isaac-beans/config/hail/` (the `orchestration-*`
  bands and `_orchestration-template.edn`).
- `tonotop/planning` → `orchestration/config/hail/` (the `tono-*` bands and
  `_tono-template.edn`).
- **zanebot** → `~/.isaac/config/hail/` — the `isaac-*` bands
  (`isaac-work.md`, `isaac-plan.md`, `isaac-verify.md`, `ci-failure.md`,
  `_isaac-template.edn`) live **only on the host**, tracked nowhere. Confirmed
  2026-09-21: `orchestration/isaac-beans/install.sh` deploys only the
  `orchestration-*` set.
- **Yopp** — a second Isaac host with its own band set (Micah, 2026-09-23:
  "once we deploy, we will need to migrate all of the hail bands on Zanebot and
  Yopp"). Its address and inventory still need recording here.

Order of operations, per the rule those repos already carry: edit and commit the
orchestration trees **first**, then install to each host. Never hand-edit a band
on a host that has a tracked source.

The untracked `isaac-*` bands on zanebot are a standing hazard of their own —
they are why a stale `:prefer` key survived from July and why the
band-vs-skill contradiction in `isaac-work.md` went unnoticed. Worth tracking
them somewhere as part of this, or as its own bean.

## Interaction with isaac-69d9

isaac-69d9 retires the `:frontmatter?`/`:companion` berth in favour of the `_`
body sentinel. It explicitly does **not** cover bands, because a band's body
becomes its prompt through hail's own resolution rather than through
`:companion`. If both land, a band file ends up needing `:_base` **and** a
`prompt: _` sentinel. Sequence them deliberately and migrate each band file once,
not twice.

## Acceptance

- A band declaring `:_base` inherits from a `_`-prefixed sibling exactly as it
  does today under `base:`.
- `band_resolve.clj` no longer contains `template-band?`, `merge-bands` or
  `base` handling; hail's suites stay green.
- `_`-prefixed bands remain non-addressable — not hailable, not listed.
- Both orchestration trees are committed before any host is touched.
- zanebot and Yopp both dispatch and deliver a hail on a migrated band.
