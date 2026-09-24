---
# isaac-j4jr
title: 'Dangling pins, structural fix: verify repins downstream before squash; bb lint-pins (fetch-reachability) in every bb ci; dev-local for in-flight cross-repo beans'
status: in-progress
type: feature
priority: high
tags:
    - ci
    - process
    - unverified
created_at: 2026-09-18T04:53:38Z
updated_at: 2026-09-24T23:44:02Z
---

Structural follow-up to isaac-lsz2 (the symptom fix). Root cause: a bean spanning two repos must point the downstream repo at the upstream BEAN BRANCH while in flight; verify then squash-merges that branch into a new sha and deletes it, so the pin dangles (cli-server → foundation 3963266; isaac-server → agent b6284e42). A rule alone cannot fix a workflow that requires the bad pin temporarily.

## Decisions (2026-09-18, Micah)

1. **Verify repins before it merges.** For a multi-repo bean, verify merges the upstream repo first, then rewrites the downstream branch's pins from the pre-squash sha to the squashed main sha (`grep -l <old> deps.edn bb.edn` → sed → commit on the bean branch), re-runs that repo's CI, and only then squash-merges it. Step in `.toolbox/commands/verify.md` + the squash helper script (wherever verify's squash lives — `isaac/.toolbox/skills/…`; find it, don't add a second).
2. **`bb lint-pins` in every repo's `bb ci`, fails fast.** For each isaac-* `:git/sha` in `deps.edn` and `bb.edn`: `git fetch --depth 1 <url> <sha>` into a scratch dir (GitHub serves only ref-reachable shas, so a failed fetch IS the reachability test; no ancestry logic). Lives in isaac-foundation next to `lint-cli-host` (`isaac.foundation.pin-lint/lint!`), reusable by module repos exactly like `lint-cli-host`. Cache fetched shas under `~/.gitlibs` or a scratch dir so repeated runs are cheap; skip with `ISAAC_LINT_PINS=0` for offline runs (prints a warning, does not fail).
3. **In-flight cross-repo development uses `:dev-local`, not sha pins.** The downstream repo runs against the sibling checkout during the bean; the sha pin changes only at handoff, and after (1) it is always a main sha. Document in `isaac/AGENTS.md` (bean workflow, cross-repo section) and the hail-bean-work skill.

Rejected: `--no-ff` merges (keeps every bean commit reachable but gives up one-commit-per-bean history); tagging the pre-squash head (keeps the object alive but pins would point at pre-squash code).

## Acceptance

Specs (isaac-foundation `spec/isaac/foundation/pin_lint_spec.clj`): (a) a pin whose sha the fetch seam reports reachable passes; (b) an unreachable sha fails naming file, dep and sha; (c) `ISAAC_LINT_PINS=0` warns and passes; (d) non-isaac deps (c3kit, gherclj, …) are ignored. Fetch is a seam (`*fetch-sha*`) so specs never touch the network.

```
cd isaac-foundation && bb spec spec/isaac/foundation/pin_lint_spec.clj && bb lint-pins && bb ci
```
Then: `bb lint-pins` wired into `bb ci` of isaac-agent, isaac-server, isaac-cli-server, isaac-cli-proxy, isaac-acp, isaac-hail, isaac-hooks, isaac-mcp, isaac-discord, isaac-imessage, isaac-claude-code, isaac-episodes, isaac-foreman, isaac-worksite, isaac-cron (one commit each; green means every current pin is reachable — isaac-lsz2 must land first for cli-server/isaac-server). verify.md + squash helper + AGENTS.md updated. One-time check (not a permanent scenario): re-run the reachability sweep from isaac-lsz2 → empty.

## Worker progress (scrapper@isaac-work-2, 2026-09-19) — wrap-up / resume

### Done

- isaac-foundation `bean/isaac-j4jr` @ `d666371` (base origin/main@`df64bf1`). Pushed.
  - `spec/isaac/foundation/pin_lint_spec.clj` 4/0 (reachable / unreachable names file+dep+sha / ISAAC_LINT_PINS=0 / non-isaac ignored).
  - `spec-support/src/isaac/foundation/pin_lint.clj` — `*fetch-sha*` seam; `git fetch --depth 1` default.
  - `bb lint-pins` task + wired into foundation `bb ci`.
  - `ISAAC_GIT=1 bb spec` 1058/0. `bb lint-pins` ok.
  - Full `bb features` 197/2 pending 2 — both `modules_pins.feature` failures are pre-existing (gitlibs fixture-agent path under verify/isaac-foundation; feature file not in this branch's diff).
- isaac (beans/docs) `bd45b5d3`: verify.md §6a (repin downstream before squash; hail-bean-verify §7a is the squash helper — no second script), AGENTS.md cross-repo `:dev-local`, hail-bean-work skill same.

### Next (resume here)

Wire `bb lint-pins` into `bb ci` of each listed module — worktree `bean/isaac-j4jr` from each sibling; never edit shared main checkouts. One commit each. Do not pin at `d666371` until foundation lands (use `:dev-local` for in-flight). After foundation lands, pin test-support at the squash sha.

Repos: isaac-agent, isaac-server, isaac-cli-server, isaac-cli-proxy, isaac-acp, isaac-hail, isaac-hooks, isaac-mcp, isaac-discord, isaac-imessage, isaac-claude-code, isaac-episodes, isaac-foreman, isaac-worksite, isaac-cron.

Command to resume: `beans show isaac-j4jr` then worktrees from each sibling on `bean/isaac-j4jr`.

## Re-dispatch (planner, 2026-09-24)

Resume per "Next (resume here)" above. The foundation half is on `bean/isaac-j4jr` (d666371): land it first (gate/verify path as applicable), then wire `bb lint-pins` into each listed module's `bb ci`, one commit per repo, pinned to the foundation squash sha. Checkpoint to this bean after each repo so a stall loses at most one repo's work.

## Worker progress (scrapper@isaac-work-3, 2026-09-24) — conflict → planner

### Done

- isaac-foundation `bean/isaac-j4jr` rebased onto origin/main@b3db42f and pushed:
  da92fb1 (lint-pins seam) + 0b55d44 (fetch each url+sha once per file — modules
  pin five foundation deps at one sha). `pin_lint_spec` 5/0; `bb lint-pins` ok;
  `bb ci` specs 1272/0; features 229/4 — all 4 in `cli/modules_pins.feature`,
  which fail identically on origin/main (stale `~/.gitlibs/_repos/file/REL/fixture-agent`
  remote pointing at `work-1/isaac-foundation-rxun/fixture-agent`, env-only).

### Conflict (why module wiring was not started)

`bb bean-gate verify isaac-j4jr` → exit 2 (no feature-baseline), so the worker
may not land foundation; verify does. The re-dispatch asks for modules "pinned
to the foundation squash sha", which does not exist until verify lands — and
decision 3 forbids in-flight bean-branch sha pins, while `bb.edn` has no
`:dev-local` alias to use instead (lint-pins lives in test-support, loaded via
bb.edn `:deps`).

Second finding: modules pin all five foundation deps at one sha, and those shas
vary (agent/hail/hooks/episodes/cron 9ab2527; cli-proxy/claude-code/foreman/
worksite df64bf1; server fae35d6; acp 1afd934). Moving deps.edn foundation to
a newer sha is a fleet upgrade that cascades `bb pins` coherence (server pins
agent, whose deps.edn requires foundation 9ab2527), not a one-commit wiring.
Bumping only bb.edn's test-support pin (pins coherence reads deps.edn only)
keeps it to one commit per repo but splits test-support from product foundation
in bb's classpath — a design call for the planner.
Not checked out locally: isaac-cli-server, isaac-mcp, isaac-discord, isaac-imessage.

### Proposed

Split: (1) this bean = foundation half (ready at 0b55d44) → verify lands it;
(2) follow-up bean: wire `bb lint-pins` into the 15 modules via bb.edn-only
test-support bump to the landed foundation main sha (or a fleet foundation bump,
planner's call).



## Planner adjustment (2026-09-24, prowl@isaac-plan) — foundation-only; module wiring split

Conflict: `bb bean-gate verify` exits 2 (not gated), so the worker cannot land foundation — verify does. The re-dispatch required 15 modules pinned to a squash sha that does not exist until that land. Decision 3 forbids in-flight bean-branch sha pins; `bb.edn` has no `:dev-local`. Modules pin foundation at four shas; a `deps.edn` bump is a fleet upgrade, not one-commit wiring.

**Decision: this bean is the foundation half only. Do not wire modules here. Do not bump fleet foundation pins. Do not require `modules_pins.feature` 0** — those 4 failures are on origin/main (stale gitlibs fixture-agent), env-only.

### Split (draft — human promote)

- **isaac-j4jr** (this) — isaac-foundation `bean/isaac-j4jr` @ `0b55d44` → verifier lands.
- **isaac-xzef** (draft) — after foundation `main-sha`, wire `bb lint-pins` into the 15 modules via **bb.edn-only** test-support bump to that landed sha. Do **not** bump `deps.edn` product foundation pins (fleet upgrade is separate).

### Controlling acceptance (this bean)

isaac-foundation `bean/isaac-j4jr` @ `0b55d44` (or rebased / squash equivalent):

    bb spec spec/isaac/foundation/pin_lint_spec.clj
    bb lint-pins
    bb spec

0 failures on pin_lint. `bb lint-pins` ok. Docs already on isaac main (`verify.md` §6a, AGENTS.md `:dev-local`). Do **not** require full `bb features` / `bb ci` features exit 0 for the 4 pre-existing `modules_pins.feature` reds.

### Worker now

1. Do not start module wiring on this bean.
2. Hand foundation to verifier. Do **not** land. Do **not** pin modules at `0b55d44`.
3. Completing this bean unblocks the draft (after human promotion).

## Worker handoff (scrapper@isaac-work-3, 2026-09-24) — foundation to verify

Controlling acceptance re-run on isaac-foundation bean/isaac-j4jr @ 0b55d44
(base origin/main@b3db42f, clean worktree):

- `bb spec spec/isaac/foundation/pin_lint_spec.clj` → 5 examples, 0 failures
- `bb lint-pins` → `lint-pins: ok`
- `bb spec` → 1272 examples, 0 failures

`bb bean-gate verify isaac-j4jr` → exit 2 (no feature-baseline). Tagged
unverified; handed to verify band. Not landed. No module wiring (isaac-xzef).
