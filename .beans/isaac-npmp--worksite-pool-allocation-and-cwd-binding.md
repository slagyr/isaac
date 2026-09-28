---
# isaac-npmp
title: Worksite pool allocation and CWD binding
status: completed
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-28T14:31:52Z
parent: isaac-q3u3
blocked_by:
    - isaac-ey7a
    - isaac-i5lv
---

Likely repos: **isaac-worksite** and **isaac-agent**. Design: Micah + planner, 2026-09-27. Extends completed W1 (isaac-l3ps); does not assume this repo must survive the cutover.

## Contract to plan

- Contribute a worksite resource-pool type (isaac-ey7a contract) backed by named directories. Acquisition chooses and exclusively leases one free member, not the entire pool. `try-acquire` never blocks: a free member or `:busy`.
- The lease receipt binds `:session/cwd` for **that turn** before its charge is built. Sessions are independent of directories: any session the session pattern selects can run in any free member, and the same session may run its next turn in a different member.
- Operator lock/unlock, stale-holder recovery (via release-identity reconciliation on restart), and release on every turn outcome remain valid. Cross-process acquisition must be exclusive; a read-then-write race cannot claim the same member twice.
- The number of free directory members, together with free sessions, determines concurrency. A request waits when either is exhausted and wakes when a member is released.
- Decide repo placement after the behavior lands: keep Worksite if its directory-specific policy has an independent surface, otherwise move that small implementation into Agent and retire the separate module in a follow-up.

## Scenario plan to review

1. Two members admit two turns; the third waits and takes the next released member.
2. A turn runs at its leased member's cwd and reads that directory's boot context.
3. One session's consecutive turns run in whichever member is free, not a fixed one.
4. An operator lock excludes its member while another free member remains usable.
5. Separate processes cannot acquire the same member simultaneously.

Draft until scenarios are committed and baselined. No database-pool implementation is in scope.


## Decisions (2026-09-27, Micah + planner)

1. **A worksite is a pool instance**: `:resource-pools {"decks" {:type :worksite :members ["/abs/path" …]}}` (root and `config/resource-pools/<name>.edn`). The `:worksites` config key is removed and hard-rejects — one config entry per thing.
2. **Leases, locks, and state are per member**, and a member is its directory path. CLI stays `isaac worksites`: `list` shows each pool's members as free / leased (session) / locked (operator); `lock` / `unlock` take a member path; a path outside every worksite pool errors.
3. **First free member in config order wins** — deterministic.
4. **Pools bind cwd, never read it.** Inferring the member from the session's cwd is removed, as is the `:worksite-busy` refusal: busy means wait. The `:worksite` turnstile is deleted (Agent removes turnstiles in isaac-ey7a).
5. **Cross-process exclusivity is a unit spec**, not a scenario: concurrent acquires against the file lock never both win a member.
6. The old "turns outside any worksite sail through" scenario is dropped: a turn that names no pool never touches one.

## Acceptance

Features: `isaac-worksite/features/worksite/registry.feature` and `lock.feature`, both rewritten `@wip` on main at 0dbf76b. Remove `@wip`; all pass:

- [ ] `bb features features/worksite/registry.feature` — `:13` validation (incl. old key rejected), `:44` list states
- [ ] `bb features features/worksite/lock.feature` — `:27` two members + third waits, `:50` one session across members, `:67` operator lock + waiting + unlock/tick, `:89` lock/unlock by path, `:107` failed turn releases, `:128` dead-pid lease stolen, operator lock not
- [ ] Step `a stale turn lock holds worksite {string} with pid {int}` keeps its wording; its argument is now a member path.
- [ ] Scenario `:27` has two `the user sends … with resource pools` turns in flight at once; if the Agent step keeps only one turn future, extend its internals (same phrase) in isaac-agent's `session_steps.clj`.
- [ ] Unit spec: concurrent acquires on one member — exactly one wins.
- [ ] One-time check: `git grep -i turnstile` and `git grep ':worksites'` in isaac-worksite find nothing outside the removed-key validation.
- [ ] Repin isaac-agent to the main sha that lands isaac-ey7a + isaac-i5lv; `bb verify` green; version bump; bump the worksite entry in the isaac repo's modules.edn registry.

feature-baseline: isaac-worksite 0dbf76b31fb9606e104a2224a7b05485608133d5
feature-blob: isaac-worksite features/worksite/registry.feature 1803468dfa13f297c854a2c351f8f4b5224a729a
feature-blob: isaac-worksite features/worksite/lock.feature f8d38eb9c4818b5bb17474c70b13de3cbc3e5301

## Worker checkpoint (2026-09-28)

Done: Agent member-schema change on `bean/isaac-npmp-rebased` at 8f9fd19 (rebased against current main); `bb ci` green (1796 specs, 905 features, one pre-existing pending). Worksite branch `bean/isaac-npmp` at f2d3e8b, `bb ci` green (16 specs, 8 features), including all baselined scenarios without @wip. Next: gate from isaac with explicit checkout paths; land Agent first, repin Worksite from upstream pre-squash SHA to Agent main SHA (`bb.edn:31`, `deps.edn:9`), then land Worksite and bump `modules.edn:52`. Note: prior Agent branch remote contains original pre-rebase commit; current rebased branch is pushed separately, no force-push. Resume at `isaac-worksite/bb.edn:31` after Agent landing. 

## Landed on main (2026-09-28)

main-sha: isaac-agent 92cd8a1307d28b13f378e6a53aa4b2ff06618356
main-sha: isaac-worksite 8d9ab8aea1026ca88d4d7d5af24deceb8bdf58f3
main-sha: isaac 4019a454e6708b720eedc5ec4fa18996df434335

Verification: Agent `bb ci` green (1796 specs, 905 features, one pre-existing pending); Worksite `bb ci` green (16 specs, 8 features). `bb bean-gate verify isaac-npmp --dir isaac-worksite=../isaac-worksite --dir isaac-agent=../isaac-agent-kleb` PASS on squash commits. Worksite version 0.1.2; module registry repinned to landed main.
