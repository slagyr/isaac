---
# isaac-a5dx
title: config set refuses creating a key the schema does not declare inside a schema'd map (policy split from isaac-cgxa)
status: in-progress
type: feature
priority: normal
tags:
    - foundation
    - config
created_at: 2026-09-22T22:45:06Z
updated_at: 2026-09-22T23:31:47Z
---

## Problem

`isaac config set comms.gchat.gchat.allow-from …` (the pre-cgxa mis-parse) wrote
`{:comms {:gchat {:gchat {:allow-from …}}}}` — a key the composed schema does
not know, inside a map it does know — and exited 0. isaac-nq4c made unknown
keys warn on load instead of vanishing; isaac-fun8 made value-validator errors
refuse a set. The gap left: a set that would CREATE an unknown key under a
schema'd map still writes and warns later, so a typo in a path lands in config.

Split out of isaac-cgxa (2026-09-22): that bean fixed the grammar (namespaced
segments stay whole); this is the policy.

## Decision to make (Micah)

Refuse, not warn: `config set` and `config unset` on a path whose parent is a
schema'd map refuse a segment the schema does not declare (static or a berth's
dynamic `:extra-schema`), with the same exit/message shape as a validator
refusal (fun8), listing the known keys at that level. `--force` (isaac-9mkp)
still writes. Open maps (`:key-spec`/`:value-spec` entity tables) accept any
key, as today.

## Scenarios (foundation features/cli, Marigold)

- set of an undeclared key under a schema'd map is refused; nothing written;
  the message names the parent path and the known keys.
- set of an undeclared key under an entity table (key-spec) still writes.
- `--force` writes the undeclared key and the load-time warning fires.
- unset of an undeclared key under a schema'd map is refused the same way.

## Acceptance

`bb spec`, the new feature, `bb ci` green in isaac-foundation; agent's
config-set scenarios (fun8) still green against the new foundation sha.

## Decision (Micah, 2026-09-22)

Refuse by default; `--force` writes the undeclared key and the load-time warning still fires. "A force lets you get away with it." Ready for a worker; scenarios above stand.

## Handoff (worker, 2026-09-22)

Implemented in isaac-foundation, branch `bean/isaac-a5dx` @ 01d81b9d4f4ee731b2484366d4aaac04c2fe0702 (pushed).

**Where the refusal lives:** `isaac.config.mutate/set-config` and
`unset-config` (`src/isaac/config/mutate.clj`), not the CLI layer. Both
now compute the composed root schema from the fresh `load-config-result`
they already load for pre-errors (`schema-resolve/root-schema-for`), and
call a new private `undeclared-key-refusal` before building/validating
any write plan. It walks `nav/path->spec` (unchanged semantics: dynamic
`:key-spec`/`:value-spec` maps — crews, models, berths, relays, … —
always advance regardless of the segment; only a STATIC schema'd map can
fail) and, when the walk fails specifically because a static map doesn't
declare the segment, returns `{:key path :value <message>}` in the exact
`:errors` shape a fun8 validator error uses — so it flows through the
same `:status :invalid`, nothing-written path, and the CLI's existing
"(use --force to write anyway, or set the whole map: …)" hint. `force?`
skips the check entirely (existing write + nq4c load-time warning path,
untouched).

`isaac.config.nav/path->spec` (`src/isaac/config/nav.clj`) now also
returns `:parent-path` (dotted path consumed so far) and `:known-keys`
(sorted, namespace-preserving field names) alongside the `:ok? false`
result, only for the "unrecognized segment under a static schema" case —
omitted for the unrelated "path continues past set terminus" failure,
which carries no known-keys to report.

`isaac.config.cli.mutate-common/set-config!` (`src/isaac/config/cli/mutate_common.clj`)
no longer pre-refuses on `nav/path->spec` before calling
`mutate/set-config` — that old gate ignored `--force` entirely (a
pre-existing bug this bean also fixes) and had a different, unhinted
message shape. It now only consults `path->spec` for set-typed member
detection, same as `unset-config!` already did; the refusal itself comes
from `mutate/set-config`'s new check.

**Message shape:** `unknown key "<segment>" — <parent.path> knows: <k1>,
<k2>, …` (e.g. `unknown key "bogus-field" — relays.helm-station knows:
crew, helm/freq, helm/outer, type`), rendered via `common/print-errors!`
as `error: <path> - <message>`, followed by the existing set-only
"(use --force …)" hint. Exit 1, nothing written.

**Files:** `src/isaac/config/mutate.clj`, `src/isaac/config/nav.clj`,
`src/isaac/config/cli/mutate_common.clj`; specs
`spec/isaac/config/mutate_spec.clj`, `spec/isaac/config/nav_spec.clj`;
feature `features/cli/config_set_undeclared_key.feature`.

**Existing-behavior note:** a handful of pre-existing specs wrote to keys
that were never actually declared under their static schema (e.g.
`berths.<id>.experimental`, `signals.<id>.bogus`, and the parlor fixture's
`signals.<id>.parlor/mood` "kind/field"-prefixed writes — that comm-kind's
extra-schema declares the bare `:mood`/`:color`/`:loft`, not a namespaced
key, so the prefixed spelling was never schema-recognized even before
this bean). Those now correctly refuse without `--force`; updated in
place to assert the refusal, with a `:force? true` companion each where
the point of the original test was write/warn behavior, not path
recognition.

**Test commands + counts:**
- `bb lint` — 0 errors (292 pre-existing warnings, none in touched files).
- `bb spec` — 1143 examples, 0 failures.
- `bb features features/cli/config_set_undeclared_key.feature` — 4 examples, 0 failures.
- `bb features features/cli/config_set_namespaced.feature` (isaac-cgxa's own feature) — 4 examples, 0 failures.
- `bb features` (whole suite) — 206 examples, 2 failures, 2 pending. The 2 failures are
  `features/cli/modules_pins.feature` ("a sibling pinned at an ancestor of
  the registry sha fails the check" / "… ahead of the registry passes with
  a note") — pre-existing, unrelated: a stale `~/.gitlibs` fixture-agent
  git cache on this machine pointing at a sibling `work-2` checkout path,
  as flagged in the bean brief. Not touched.
- `bb ci` — runs `bb spec` (green) then `bb features`; exits non-zero
  solely because of the same 2 pre-existing `modules_pins.feature`
  failures above.

**Scenarios** (`features/cli/config_set_undeclared_key.feature`, reusing
isaac-cgxa's `marigold.cgxa.bridge`/`marigold.cgxa.longwave` fixture):
1. set of an undeclared key under a schema'd map is refused (exit 1,
   message names `relays.helm-station` and its known keys, nothing written).
2. set of an undeclared key under an entity table (`:relays`' own
   `:key-spec`) still writes — a brand-new relay id, no `--force` needed.
3. `--force` writes the undeclared key; the nq4c load-time warning
   (`:config/unknown-key`) still fires.
4. unset of an undeclared key under a schema'd map is refused the same way.

Left `in-progress`, no tags, per instructions.
