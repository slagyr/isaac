---
# isaac-j95x
title: ACP session/new on an episodes crew opens main's session named "session" — --crew marvin lands the user on crew main
status: completed
type: bug
priority: high
created_at: 2026-09-24T20:41:09Z
updated_at: 2026-09-24T21:33:35Z
---

Micah, 2026-09-24: `toad acp "zane-isaac acp --crew marvin"` → "Why did I get main crew?" zanebot cli.log: argv `["acp" "--crew" "marvin"]`, then `:session/behavior-resolved :crew "main" :session "session"`; `~/.isaac/sessions/main/session` (created 2026-07-10) was updated by his turn.

## Cause (three layers)
1. `isaac.comm.acp.server/session-new-handler`: with no `:name` in params (Toad never sends one) it uses `(policy/default-session sess crew-id …)`. Marvin's crew is `:session-policy :episodes`; **`EpisodesPolicy/default-session` returns nil** (episodes.clj:243) — by design "a fresh id" — but the server then calls `open-acp-session! sess nil crew-id …`.
2. `EpisodesPolicy/open-session!` does `(session-id* name)` = `(str nil)` = `""` and `(or (store/get-session store "") (store/open-session! …))`; the sidecar store resolves that degenerate id to the existing session `session` (under crew main) and returns it — an existing session of ANOTHER crew, with `:crew "main"` kept.
3. The chronicle policy has the same shape of bug: `default-session` falls back to `most-recent-session` across ALL crews when the crew has none.

## Fix
- acp server: when `default-session` returns nil, mint a fresh session id (`policy/open-session!` with a generated name, e.g. `acp-<timestamp>-<rand>` or the episodes id scheme) — never call open-session! with nil.
- episodes + chronicle `open-session!`: opening an id that exists under a DIFFERENT crew than `:crew opts` is an error (or the acp server must check `(:crew existing)` = crew-id and refuse/mint). No silent cross-crew attach.
- chronicle `default-session`: drop the cross-crew `most-recent-session` fallback; nil when the crew has no sessions.
- sidecar `get-session ""`/`session-id ""` must not resolve to a real session.
- Scenario (isaac-acp): `isaac acp --crew marvin` on a root where marvin is an episodes crew and main has a session → session/new returns a NEW session whose crew is marvin; a second `session/new` returns another new one. Scenario (agent): chronicle default-session with no crew sessions → nil.

## Acceptance
- [ ] scenarios above green; `bb ci` green in isaac-acp, isaac-agent, isaac-episodes as touched; version bumps; registry repins.
- [ ] On zanebot: `zane-isaac acp --crew marvin` + session/new → behavior-resolved crew marvin.

Workaround until then: `zane-isaac acp --crew marvin --session <an existing marvin session id>` (attach path bypasses session/new's defaulting).

Repo scope: isaac-acp (server.clj), isaac-episodes (policy/episodes.clj), isaac-agent (policy/chronicle.clj, store/sidecar.clj).

## Handoff

Implemented all three layers; ungated flow, tagging `unverified` now.

**Branches (all `bean/isaac-j95x`, pushed):**

| Repo | sha | gate |
|---|---|---|
| isaac-agent | `8cfd44dad00d57d15ae5bd6f2fac048046d5b84b` | `bb lint`, `bb spec` (1734 examples, 0 failures), `bb features` (850 examples, 0 failures, 1 pre-existing pending) all green |
| isaac-episodes | `a768d274f0bccd0b225c8a85d1aa290e291f5af9` | `bb spec` (218 examples, 0 failures), `bb features` (85 examples, 0 failures), `bb lint-cli-host` ok |
| isaac-acp | `6030a978463d1245342118c68b1d540032c1424b` | `bb spec` (78, 0 failures), `bb features` (65, 0 failures — includes the new scenario), `bb config-bypass-lint`/`bb lint-cli-host` ok |

**Landing order:** isaac-agent → isaac-episodes → isaac-acp (acp depends on both; episodes does not depend on the agent-layer fix — see below).

**Repins after landing:**
- isaac-episodes' `deps.edn`/`bb.edn` pin isaac-agent at `da9214aa72786fd830847c5542f5ea7781a44410` — bump to isaac-agent's landed main sha for hygiene (protocol/store contract consistency). Not required for isaac-episodes' own tests: `EpisodesPolicy/open-session!`'s new guard (blank-name refusal, cross-crew refusal) is self-contained — it never delegates to `store/get-session`/`store/open-session!` with a blank id in the first place, so it doesn't depend on the sidecar/memory-store blank-id fix.
- isaac-acp's `deps.edn`/`bb.edn` pin isaac-agent at `8aecfc3a57fef0803c35f81bd4a3f76292b227f1` and isaac-episodes at `0cbe24b55a2d94be0579a2400a11163748d5913d` — **both should be bumped** to the landed main shas so the full defense-in-depth (chronicle default-session, sidecar/memory blank-id, episodes blank/cross-crew refusal) is actually live in production, not just the ACP-layer mint-fresh-id fix. Note: isaac-acp's *currently pinned* isaac-episodes sha already has `EpisodesPolicy/default-session` minting a fresh `ids/timestamped-id` (not nil) — older than the "no default, nil" version on isaac-episodes main today. Repinning changes that behavior too; feature scenario was written to be agnostic to which minting scheme is in effect (asserts distinctness/crew/non-"session", not a literal id format) so it passes either way.

**Cross-repo testing note:** `clojure -M:dev-local:features` in isaac-acp with isaac-agent (and isaac-episodes) overridden to these worktrees currently throws `ClassCastException` in `isaac.config.resolve/resolve-crew` — reproduces identically against an **unmodified** `../isaac-agent` + `../isaac-foundation` sibling combo too (confirmed by testing with dev-local pointed at the plain, un-forked siblings), so it's pre-existing environment/version-skew in this dev-local combo, not caused by this bean. Validated instead via: (1) native `bb spec`/`bb features` in each repo against its own pin (isaac-agent/isaac-episodes fully green, isaac-acp fully green including the new scenario against its *current* pin), (2) `bb spec` and `bb features` in isaac-agent and isaac-episodes standalone (both fully green, no dev-local needed there).

**Design decisions:**
- **Cross-crew refusal (`SessionPolicy/open-session!`):** every implementation refuses (throws `ex-info` with `:reason :crew-collision`) rather than silently returning/reusing a session that belongs to a different crew than requested. Documented in the `SessionPolicy/open-session!` protocol docstring (isaac-agent `src/isaac/session/policy.clj`).
- **Blank/nil session name:** episodes refuses outright (throws `:reason :blank-session-name` — episodes never mints its own name, by design). Chronicle/sidecar/memory store mint a fresh name instead (pre-existing behavior for `nil`; extended to blank `""` too, since `(or "" ...)` previously let it survive unchanged into `session-id`'s blank→"session" fallback).
- **ACP mint scheme:** `acp-<yyyy-MM-dd-HHmm>-<4 chars>`, using `isaac.tool.memory/now` (test-controllable clock) rather than `Instant/now` directly, matching the codebase's clock-virtualization convention. Did not reuse `isaac.episodes.ids/timestamped-id` — isaac-acp's `deps.edn` doesn't put isaac-episodes on the base (non-test) classpath, and ACP staying generic (not depending on an episodes-specific concept) matches the project's "drive/surfaces stay generic" architecture stance.
- **ACP crew-mismatch check:** `session-new-handler` also verifies `(:crew session) = crew-id` after opening and returns a JSON-RPC `-32602` error if not, as a defensive second layer on top of the SPI's own refusal (covered by a unit spec in `server_spec.clj` using `with-redefs` on `policy/default-session` to force the collision, since it's not otherwise reachable through the normal mint-fresh-id path).

**Test counts (new/changed):** isaac-agent +9 spec examples (chronicle_spec.clj new, sidecar_spec.clj +2, impl_common_spec.clj +3, sidecar_impl_spec.clj +1, memory_spec.clj +1) and 2 protocol/production files documented; isaac-episodes +4 spec examples in episodes_spec.clj; isaac-acp +2 spec examples in server_spec.clj and 1 new feature scenario in episodes.feature.

Version bumps: isaac-agent 0.1.81→0.1.82, isaac-episodes 0.1.4→0.1.5, isaac-acp 0.1.14→0.1.15.

## Landed on main

- main-sha: isaac-agent 8cfd44dad00d57d15ae5bd6f2fac048046d5b84b (0.1.82) — spec 1734/0, features 850/0.
- main-sha: isaac-episodes 01b538a1ed8cdc557ab8bde4209dd772f98f7926 (0.1.5) — agent repinned to 8cfd44d; spec 218/0, features 85/0.
- main-sha: isaac-acp 6030a978463d1245342118c68b1d540032c1424b (0.1.15) — spec 78/0, features 65/0 against its existing pins. NOT repinned: acp still pins pre-ruom foundation/agent and reads [:defaults :crew] at the old path, so the ruom-era agent cannot be pinned until the acp 0r95 follow-up lands (filed separately). Runtime uses the host's installed agent/episodes, so the fix is live once the three modules are upgraded.

Registry repinned for all three. Deploy: zanebot upgrade agent + episodes + acp, restart, verify `zane-isaac acp --crew marvin` resolves crew marvin.
