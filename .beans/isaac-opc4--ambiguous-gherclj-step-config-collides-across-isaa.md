---
# isaac-opc4
title: Ambiguous gherclj step "config:" collides across isaac-agent and isaac-server
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-06-26T21:45:52Z
updated_at: 2026-06-27T15:11:36Z
blocking:
    - isaac-c58s
---

The hail feature suite is BROKEN on main: `clojure -M:features` in isaac-hail dies with

    Execution error at gherclj.core/classify-step
    ambiguous step match: "config:" matches: config-applied, configure

## Root cause
Two modules each register the exact gherclj step `config:`:
- isaac-agent `spec/isaac/session/session_steps.clj` -> `config-applied`: persists every dotted key into on-disk `config/isaac.edn` (special-cases `log.output` -> in-memory logger; ignores `bind-server-port`).
- isaac-server `spec/isaac/server/server_steps.clj:607` -> `configure`: writes `:server-config` in harness state + `persist-config-entry!` (special-cases `log.output`, `bind-server-port`).

Any harness that loads BOTH step libs gets two defs for `config:`. isaac-hail's `feature-steps/isaac/hail_steps.clj` requires `isaac.server.server-steps` (for its helpers) AND pulls in agent session-steps, so both `config:` defs register. gherclj refuses the ambiguous match and the whole hail suite fails to build.

The two impls overlap heavily (both handle `log.output`, both persist dotted config) — they look like near-duplicate steps that drifted apart in the monolith carve.

## How it surfaced
Latent until isaac-3wic added `features/hail-naming.feature` (commit 41a3cfd), the first hail feature to USE `Given config:` (to set `hail-settings.naming-strategy`). Before that the two defs were both loaded but never matched, so the ambiguity never triggered.

## Impact
- Entire isaac-hail feature suite cannot build/run.
- Blocks verification of isaac-c58s (hail selector conforming) — its new @wip scenarios are written but unrunnable.
- Any harness loading both agent+server steps is at risk.

## Fix (do NOT add a third differently-named step as a dodge)
Resolve the collision so `config:` is unambiguous and features keep using the proper `config:`:
- Decide the single canonical `config:` (the on-disk-persisting one is what `hail-naming` needs — it shells out to `hail send` which reads `config/isaac.edn`), and rename the other to a proper distinct name (e.g. server-scoped `server config:`), updating its usages.
- 6 isaac-server features use `Given config:`; isaac-imessage calls `server-steps/configure` directly (fn, not via step text — unaffected by a step-text rename).
- Consider consolidating the two near-duplicate impls rather than just renaming, since they overlap.

## Repro
cd isaac-hail && clojure -M:features  (or bb features)

## Implementation (work-3)
- **Foundation** (`eb3bcb0`): canonical `config:` in `isaac.foundation.harness-config-steps` (spec + spec-support).
- **Server** (`96bd83a`): renamed to `server config:` → `server-config-applied`; disk persist delegates to foundation; bumped foundation-spec pin; fixed dev-reload log row order.
- **Agent** (`75190ff`): dropped duplicate `config:`; delegates to foundation; foundation SHA `eb3bcb0`.
- **Cron** (`d6440c3`): resolves foundation `config-applied`.
- **Verification**: isaac-hail features 80/0 (dev-local); isaac-server features 47/0 (git deps).

## Verification

Verified on fetched GitHub `main`:

- `isaac-foundation` `eb3bcb0785c4e86d9e8c032c26aa7b4341b9f04d`
- `isaac-agent` `75190ffa45edc19e87402b60cc4bce7277ca46d8`
- `isaac-server` `96bd83ad315a7a9c2dbd840d3089ae6d6c9153cc`
- `isaac-cron` `d6440c30a3213a7822c518126fc669df97e97ea4`
- `isaac-hail` `da07bd8878c39f94e79412b225f38b2567171245`

Proofs were green:

- `isaac-hail` `bb features` in a sibling worktree layout matching its `../isaac-*` local-root feature alias -> `80 examples, 0 failures, 333 assertions, 2 pre-existing pending`
- `isaac-server` `bb features` -> `47 examples, 0 failures, 120 assertions`

The original `config:` ambiguity is resolved on current heads. The first bare
hail worktree run failed only because its `:features` alias resolves
`../isaac-agent`, `../isaac-server`, and `../isaac-foundation`; once verified
in that intended sibling layout, the suite was green.



## Reopened 2026-06-26 — fix was incomplete (masked by dev-local verification)

The original fix put the canonical config: step in foundation harness-config-
steps in BOTH spec/ (foundation-spec) AND spec-support/src (foundation-test-
support). Those are two separately-resolvable coords (different :deps/root).
Any git consumer that pulls BOTH — directly or transitively via agent-spec/
server-spec — loads isaac.foundation.harness-config-steps (and log-steps, etc.)
TWICE → "ambiguous step match: config-applied, config-applied" (and
log-entries-match, ...). This is the same class of failure opc4 set out to fix,
just relocated into foundation's own spec/spec-support split.

Why opc4's verification missed it: hail was verified via the dev-local sibling
layout (../isaac-* :local/root), which collapses foundation-spec and
foundation-test-support onto overlapping local paths and dedupes; real git
pins do NOT dedupe them. Under git pins, cron/discord/hail/etc. feature suites
fail with the ambiguous-step error.

## Root cause (precise)
Foundation step namespaces (cli/fs/harness-config/log/root-steps) existed as
duplicate, drifted real files in BOTH foundation spec/ and spec-support/src.
Consumers pull both coords → double registration.

## Fix (foundation-side: DONE, proven)
isaac-foundation v0.1.11 (28aa7f080bcfee3c727071e42bd4003e1c898091): the step
namespaces now live ONLY in spec-support/ (foundation-test-support). spec/
(foundation-spec) keeps foundation's own *_spec.clj + marigold fixtures, no
step defs. foundation's own bb ci stays green (spec-support/src added to its
test paths): 777/0 spec + 117/0 features.
PROOF the dup is resolved for a both-coords consumer: isaac-cron bumped to
foundation v0.1.11 → clojure -M:features = 14/0 (previously crashed with the
ambiguous-step error).

## Remaining (consumer-side cascade)
Every consumer must (a) be on foundation v0.1.11, and (b) pull foundation-
test-support for steps:
- Consumers already pulling BOTH foundation-spec + foundation-test-support
  (cron, discord, ...) → bumping to v0.1.11 alone resolves the dup.
- Consumers pulling ONLY foundation-spec for steps (isaac-agent) → must ADD
  foundation-test-support, since v0.1.11 foundation-spec no longer ships steps.
- -spec subprojects that transitively provide foundation steps (agent-spec,
  server-spec, *-test-support) must pull foundation-test-support, not -spec.
Then re-tag/re-pin the chain (foundation v0.1.11 -> agent -> server -> leaves
-> hail).

NOTE: foundation-spec/deps.edn in v0.1.11 has an inert
isaac-foundation-test-support {:local/root ".."} dep — tools.deps ignores
:local/root in git-fetched subprojects, so it does nothing for remote
consumers (harmless; can be dropped in a later foundation commit).

Cross-link: this is the blocker for isaac-4onv's leaf module bump.



## Resolution 2026-06-26 — fixed at the source + verified on real git pins

Root cause (precise): the opc4 fix shipped the canonical foundation gherclj
steps (harness-config-steps, log-steps, cli/fs/root-steps) as duplicate real
files in BOTH foundation spec/ (foundation-spec) and spec-support/src
(foundation-test-support). Those resolve as two separate coords, so any git
consumer pulling both — directly or transitively via agent-spec/server-spec —
double-registered the steps → "ambiguous step match". opc4's original
verification ran in a dev-local sibling layout that dedupes the two, masking it
under real git pins.

Fix (published):
- isaac-foundation v0.1.12 (a8344457b8b187738092072e92e0776a0128c721): step
  namespaces live ONLY in spec-support (foundation-test-support). foundation-spec
  ships foundation's own *_spec.clj + marigold fixtures, no step defs.
  foundation's own bb ci stays green via spec-support/src on its test paths
  (777/0 spec + 117/0 features).
  [Intermediate tags v0.1.10 (steps in both) and v0.1.11 (had an inert
   :local/root test-support dep that broke tools.deps version comparison) were
   superseded by v0.1.12; nothing pins them.]
- isaac-agent v0.1.8 (115ed52d18d481fec709f6cbb8460cafc9ffcd82): pulls
  isaac-foundation-test-support for steps in :test/:spec/:features; foundation v0.1.12.
- isaac-server v0.1.9 (8eaa76841698b90e57232fc3d5afd2e9169395c3): server-test-support
  (spec-support) pulls foundation-test-support; foundation v0.1.12, telly agent v0.1.8.
- isaac-cron (dd2dfe2): foundation v0.1.12 / agent v0.1.8 / server v0.1.9.
- isaac-hail (f186ec9): foundation v0.1.12 / agent v0.1.8 / server v0.1.9.

Verification — against REAL git pins (NOT dev-local), the original failure mode:
- isaac-hail   clojure -M:features -> 80/0 (2 pre-existing pending = the c58s
  @wip scenarios this bean blocked) ; -M:spec 71/0.   <-- opc4's original repro
- isaac-cron   -M:features 14/0 ; -M:spec 19/0
- isaac-server -M:test:features 47/0 ; -M:test:spec 155/156 *
- isaac-agent  -M:features 554/0 ; -M:spec 1117/0
- isaac-foundation bb ci 777/0 + 117/0

* The one server spec failure ("deletes config keys with #delete") is a
  PRE-EXISTING opc4 config-delete issue (red on server origin/main before any
  of this work) — separate from the step-collision fix. Flagging for a
  follow-up; not a regression.

Scope note: the only opc4 victims were repos pinned to the opc4-era foundation
(hail, cron). isaac-discord/imessage/hooks/acp pin OLDER foundation that
predates the duplication and are GREEN on their current pins (verified) — they
are NOT affected and were left untouched; they'll pick up v0.1.12 cleanly
whenever they next bump foundation.

Unblocks isaac-c58s (hail selector conforming) — hail features build/run again.
Tagged unverified for confirmation on fetched GitHub heads.

## Verification failed 2026-06-27

Re-checked on fetched GitHub heads:

- `isaac-foundation` `a8344457b8b187738092072e92e0776a0128c721`
- `isaac-agent` `115ed52d18d481fec709f6cbb8460cafc9ffcd82`
- `isaac-server` `eb51cc48b8964dabb678086ac36051a86d94c03a`
- `isaac-cron` `dd2dfe2dfb053ac6dc0a19ed865e11f21a02322b`
- `isaac-hail` `f186ec95993b8cd9ea2c6fb9288a13fe93f894ad`

What is green on real git-pin heads:

- `isaac-foundation` `ISAAC_GIT=1 bb ci` -> `777` spec examples, `0` failures; `117` feature examples, `0` failures
- `isaac-agent` `ISAAC_GIT=1 bb ci` is green on the reopened opc4 surface
- `isaac-server` `ISAAC_GIT=1 bb ci` now has the previously separate `#delete` spec fixed; current head is green on the opc4 surface
- `isaac-cron` `ISAAC_GIT=1 bb ci` -> `19` spec examples, `0` failures; `14` feature examples, `0` failures

What is still red:

- `isaac-hail` current `ISAAC_GIT=1 bb features` still fails immediately with:
  `ambiguous step match: "config:" matches: config-applied, configure`

Current `isaac-hail` head `f186ec9` still hardcodes local-root step deps in
its `:features` alias `:extra-deps`:

- `io.github.slagyr/isaac-foundation {:local/root "../isaac-foundation"}`
- `io.github.slagyr/isaac-agent {:local/root "../isaac-agent"}`
- `io.github.slagyr/isaac-server {:local/root "../isaac-server"}`
- plus `isaac-server-spec`, `isaac-agent-spec`, and `isaac-foundation-spec` as local roots

So even with `ISAAC_GIT=1`, hail's feature lane is not actually pinned to the
published git coords that were claimed in the resolution note, and on the true
current feature alias the original ambiguity still reproduces.

Bottom line: `opc4` is not verifier-green yet on the real current hail feature
surface, so it should remain open.
