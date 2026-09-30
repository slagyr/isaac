---
# isaac-c2b6
title: 'Foundation CI lint: fail on other modules'' names in src/'
status: draft
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:43:45Z
blocked_by:
    - isaac-6pqo
    - isaac-h2oo
    - isaac-v38i
    - isaac-n140
    - isaac-mxgn
    - isaac-601n
---

## Ruling

Micah, 2026-09-29: isaac-foundation's code must never name another module's
config, berths, ids or concepts. Land this LAST, once cleanup-beans 1-4 are
merged — a lint that fails on day one because the codebase it's protecting
still has violations is worse than no lint.

## Problem

Nothing currently enforces the "foundation never names another module"
rule mechanically — it was violated repeatedly (comm/, crew-exists?,
normalize.clj, entity-collections, cli/registry.clj's init scaffold,
module/berths.clj's retired messages) with no CI signal. Precedent exists:
`isaac-foundation/spec-support/src/isaac/foundation/cli_host_lint.clj` +
the `lint-cli-host` bb task already does exactly this shape of check
(regex-scan `src/**/*.clj`, fail with file:line:text on any match, small
allowlist of exempt files) for a different rule (no raw `System/exit` etc.
outside the CLI host).

## Wanted

1. New lint (`isaac.foundation.module-naming-lint` or similar, following
   `cli_host_lint.clj`'s exact shape) that scans `isaac-foundation/src/**/*.clj`
   for patterns naming a known OTHER module:
   - Namespace/keyword prefixes: `isaac\.agent\b`, `isaac\.http\b` (outside
     `isaac.module.lifecycle`'s legitimate `server-module-id`/migration-era
     references — see Open questions), `isaac\.server\b`, `isaac\.hail\b`,
     `isaac\.discord\b`, `isaac\.imessage\b`, `isaac\.acp\b`, `isaac\.cron\b`,
     `isaac\.hooks\b`, `isaac\.gchat\b`, `isaac\.gmail\b`, `isaac\.google\b`,
     `isaac\.mcp\b`, and any other module repo in the `isaac-*` family from
     `isaac/AGENTS.md`'s repo list.
   - Config-concept keywords that are exclusively agent's: `:crew\b`,
     `:providers\b`, `:models\b`, `:comms\b`, `:soul\b` — **this list needs
     care** (see Open questions: some of these words are common enough to
     collide with legitimate generic code/comments/docstrings-about-the-
     rule-itself).
2. Wire it into `bb.edn` (new task, e.g. `lint-module-naming`) and into
   whatever CI step already runs `lint-cli-host`/`lint-pins`/
   `config-bypass-lint` (check `.github/workflows/` for where those run).
3. Keep the exempt-files allowlist as small as possible — ideally empty.
   If a docstring genuinely needs to name another module as an EXAMPLE
   (like `isaac.schema.registered-in`'s docstring illustrating
   `[:registered-in? :isaac.agent/comm]` as a usage example of a fully
   generic validator), decide: is that an acceptable violation (a
   docstring, not executable naming) that the lint should skip via a
   narrower pattern (e.g. only flag inside actual code forms, not comment
   lines — `cli_host_lint.clj`'s current implementation scans EVERY line
   including comments, so this needs an explicit design choice), or should
   those docstrings get reworded to use a Marigold-style placeholder
   berth id instead? Recommend the latter (reword the docstrings) since it
   keeps the lint simple (no comment/code distinction needed) and matches
   the "generic examples, no other module's names" bar isaac-3y69 already
   set for `config schema --help`.

## Acceptance

- `bb lint-module-naming` (or whatever task name) exits 0 on current
  isaac-foundation main once cleanup-beans 1-4 are merged.
- Reintroducing any of cleanup-beans 1-4's deleted code (e.g. re-adding
  `:isaac.agent/comm` to `module/berths.clj`) makes the lint fail with a
  clear file:line message, verified by a temporary local revert during
  development of this bean (not committed).
- The lint runs in CI alongside the existing `lint-cli-host`/`lint-pins`
  checks.

## Likely repo scope

`isaac-foundation` only: new lint ns under `spec-support/src/isaac/
foundation/`, `bb.edn` task, CI workflow wiring.

## Notes

- Land LAST. Depends on cleanup-beans 1, 2, 3, 4 all being merged first —
  baseline this bean only once `grep`-ing for the patterns above on
  isaac-foundation's then-current main returns nothing outside
  `isaac.schema.registered-in`'s illustrative docstrings (see item 3) and
  `isaac.module.lifecycle`'s `server-module-id` (see Open questions).
- Not touched by isaac-dnib or the handbook beans (no file overlap) —
  purely a function of what cleanup-beans 1-4 leave behind.

## Open questions

- `isaac.module.lifecycle.clj:62` defines `(def server-module-id
  :isaac.http)` and comments at lines 41/80/87 reference `:isaac.http/*`
  berths as "every other extension kind has migrated to" — this looks like
  a REAL structural dependency (foundation's module lifecycle treats
  isaac-http as THE module that hosts the server/HTTP surface, not just an
  example), not a naming violation to clean up. Confirm with Micah whether
  `server-module-id` is legitimate foundation-owns-the-concept-of-"the
  server module" plumbing (in which case the lint needs an explicit,
  narrow exemption for this one symbol) or whether it's actually the same
  category of violation as everything else in this cleanup and needs its
  own bean. Flagging rather than assuming either way — this wasn't in the
  original known-violations list and may be intentional architecture
  (foundation needs to know WHICH module can activate an HTTP host to wire
  `activate-server!`).
- Exact keyword denylist for item 1's second bullet needs Micah's sign-off
  before implementation — false positives are likely (e.g. `:models` or
  `:crew` appearing in a code comment that's explaining the rule itself,
  as several files in this very cleanup now do). Suggest the lint
  distinguish comments/docstrings from code, or accept a short, explicit,
  reviewed allowlist of comment lines (opposite problem from "allowlist-
  free" but more honest than a lint that's routinely suppressed).
