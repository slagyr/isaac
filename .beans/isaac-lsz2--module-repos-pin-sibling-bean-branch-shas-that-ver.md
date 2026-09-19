---
# isaac-lsz2
title: 'Fleet-wide dangling pins: 11 module repos pin foundation at a squashed bean-branch sha (ad0a97b, bean/isaac-1fwl) — 5 repos'' CI red; repin everything to main'
status: completed
type: bug
priority: critical
tags:
    - ci
created_at: 2026-09-18T04:46:20Z
updated_at: 2026-09-19T01:33:31Z
---

## Problem

isaac-cli-server main CI has been red since isaac-qvhy landed (02:25Z): `Error building classpath. Commit not found for marigold.longwave … isaac-foundation.git at 3963266`. The worker pinned foundation at the sha of the sibling bean branch (1fwl's foundation leg); verify then squash-merged that branch into a different sha and deleted it, so the pin points at nothing GitHub serves. isaac-server has the same disease: its `:test` alias pins isaac-agent `b6284e42` (from the 09-11 zgfx bean), unreachable — CI is green there only because the runner's gitlibs cache still holds the object; a fresh clone cannot build.

Sweep (2026-09-18, every module repo's deps.edn + bb.edn, pins to isaac-* repos):

| repo | pin | reachable? |
|---|---|---|
| isaac-cli-server | foundation `3963266` (bb.edn ×3, deps.edn) | **no** — CI red |
| isaac-server | agent `b6284e42` (`:test` alias, bb.edn + deps.edn) | **no** — CI green by cache only |
| isaac-agent | foundation `1c8e45b` (bb.edn ×5, deps.edn) | **only via leftover branch `bean/isaac-t1om`** — a time bomb: the moment that branch is deleted, agent main stops building from a cold cache (isaac-2yh9, 2026-09-15, folded in here) |
| everything else | — | yes |

The reachability test is **reachable from `origin/main`** (`git merge-base --is-ancestor <sha> origin/main`), not "on any remote branch" — the first sweep used the weaker test and missed the agent case.

## Fix (this bean; planner-implemented)

1. isaac-cli-server: repin foundation (test-support, marigold.*, foundation) to foundation **main** `e4da6e0` (carries 1fwl/qvhy/kjzq). Surfaced one racy spec: `dispatch_spec.clj` "runs a read-only hosted command when the loaded basis is stale" exited its `-with-nexus` scope before the hosted task ran (the task is a future that reads the nexus) — hold the scope until the exit frame. `bb spec` 5/5 green after.
2. isaac-agent: repin foundation (bb.edn ×5 + deps.edn) to foundation main; `bb spec && bb features`. Known follow-up: `spec/isaac/config/agent_steps.clj:7` requires `isaac.startup.config-cache`, a namespace that exists ONLY on the leftover `bean/isaac-t1om` foundation branch (never merged; foundation main has `isaac.startup.cache` / `classpath-cache`) — this breaks every downstream repo's dev-local feature run today (hail, discord: `FileNotFoundException … isaac/startup/config_cache`). Port the steps to the main namespace.
3. isaac-server: repin the `:test` alias agent to agent main (`0e804c0` or newer). **Worker leg** — absorbs isaac-ane7 (Lifecycle reconciler 'Two comms run independently when both slots are present at boot' under dev-local — same server-vs-current-agent family; scrapped into here). Planner tried it: `bb spec` green (125), `bb features` has 4 failures against agent main (Comm extension "Multiple comm instances of the same :type coexist"; Module activation "Comm slot starts when configured at boot", "Declared module is activated during server boot even without a slot", "Module activation failure surfaces a structured error") — a week of agent changes since b6284e4; fix the features/steps for the current agent, don't pin backwards.
4. **Rule (add to isaac/AGENTS.md bean workflow + the verify checklist):** a bean may only pin a sibling repo at a sha reachable from that repo's `main` (`git branch -r --contains <sha>` includes `origin/main`). Never a bean-branch sha — verify squashes and deletes it. Verify rejects a handoff whose pins fail this check.

## Also observed (not fixed here)
isaac-cli-server `features/cli/endpoint.feature` "a reattached client receives frames buffered while detached (isaac-qvhy)" is flaky (~1 in 3 locally): the echo's two output writes ("while away", "\n") race the attach replay, and the frame matcher sees "\n" first. Needs its own look (buffer/replay ordering in dispatch, or the scenario's stdin-while-detached step) — bean it if it bites CI.

## Handoff

- isaac-cli-server `bean/isaac-lsz2` @ 5d54b56 (repin + racy spec held open; `bb spec` 5/5 green, `bb features` green except the pre-existing reattach flake noted above). Planner-implemented.
- isaac-server leg: not started — worker.

## Acceptance
```
cd isaac-cli-server && bb ci        # green on main after merge
cd isaac-server && bb ci            # green with the agent repin
```
CI green on both repos' main; the sweep above re-run shows no unreachable pins.


## Structural fix

The root cause (in-flight cross-repo pins that verify squashes away) is **isaac-j4jr**: verify repins before merging, `bb lint-pins` in every `bb ci`, `:dev-local` while in flight. This bean stays the symptom fix; the rule in "Fix 3" above is superseded by that bean.


## Re-scoped to the fleet (planner, 2026-09-18 23:50Z) — CRITICAL

The 1fwl worker's foundation branch head `ad0a97b7f039814dc92916299baf3c07a5b86f3a` was pinned by nearly every module repo during the 09-18 pin-bump wave; verify squashed isaac-1fwl to `cc53d69` and the branch `bean/isaac-1fwl` survives only by accident. It is on NO main. Current CI:

| repo | CI | pin problem |
|---|---|---|
| isaac-google, isaac-gmail, isaac-mcp, isaac-claude-code | **red** — Commit not found | foundation `ad0a97b` |
| isaac-gchat | **red** — Commit not found | isaac-google at a bean-branch sha (find it in deps.edn; pin google main) + foundation `ad0a97b` |
| isaac-http (checkout isaac-server) | **red** | foundation `3963266` + agent `b6284e4` (the legs above) |
| isaac-hail, isaac-acp, isaac-cron, isaac-discord, isaac-hooks | green/other | foundation `ad0a97b` — green only via runner cache; goes red the day the branch is deleted |
| isaac-agent | — | foundation `1c8e45b` via leftover `bean/isaac-t1om` (leg 2 above) |

**Do**: in every repo above, repin foundation (and foundation-spec / test-support / marigold.*) to foundation main `0b120cc` or newer, gchat's google pin to google main, server's agent pin to agent main, agent's foundation pin to main. One commit per repo, `bb ci` green (or the pre-existing real reds noted — discord has 3 genuine test failures and hooks 1 that are NOT pin problems; leave those to their own beans unless the repin fixes them). Do NOT delete `bean/isaac-1fwl` or `bean/isaac-t1om` until every repo is repinned and green.

Order: foundation-dependent repos first (they only need the foundation repin), then gchat (needs google repinned first), then server. Land each via verify as it goes green — do not hold the fleet for the slowest one.


**Ordering (verified 2026-09-18 23:58Z by attempting the hooks repin):** the isaac-agent leg goes FIRST. Every downstream repo loads isaac-agent's spec steps, and `spec/isaac/config/agent_steps.clj` requires `isaac.startup.config-cache`, which exists only on `bean/isaac-t1om`; against foundation main every module feature run dies with `FileNotFoundException … isaac/startup/config_cache`. Port those steps to the main namespace, land agent, THEN repin the fleet to foundation main + that agent sha. Related: isaac-7fge (hooks auth dropped) lands after its hooks repin.


## isaac-http leg — planner progress (2026-09-19 00:20Z), branch `bean/isaac-lsz2` in isaac-http @ c787b7d

**This is the leg that unblocks isaac-google / isaac-gchat / isaac-gmail**: they pin isaac-http main (db2b639+), and isaac-http main pins foundation `93ffe98` — the tdlz worker's foundation branch, squashed and deleted, on NO ref. Their reds are transitive. Order: land this leg, then bump the three Google repos' isaac-http pin (and their own foundation pin to main) — trivial once http is green.

Done on the branch: foundation `93ffe98`→main `e4da6e0` (deps.edn + bb.edn), agent `b6284e4`→main `0e804c0`; `auth_cli_spec` seeds `{:defaults {:crew :main}}` + a crew file because the composed schema (agent main) now requires `defaults.crew` and `set-config` refuses to write into an invalid root. `bb spec` 155/0.

Remaining (`bb features` 86 examples, 5 failures):
1. `features/http/config.feature` ":http bind/auth config is valid" — config table lacks `defaults.crew`; add it (same cause as the spec).
2–5. `Comm extension: Multiple comm instances of the same :type coexist`; `Module activation: Comm slot starts when configured at boot`, `Declared module is activated during server boot even without a slot`, `Module activation failure surfaces a structured error` — the `:module/activated` / `:comm/activated` log rows are absent at boot against agent main (the matcher sees `:server/hello` first). Find whether activation moved (agent 0.1.67→0.1.71 berth/lifecycle changes, isaac-oc3f/3q4m line) and recut the assertions or fix the boot path. These are the ex-isaac-ane7 family.

Agent leg (config-cache steps) still first for every OTHER downstream repo; http's own features run with the pinned agent-spec and got past loading, so http can land independently.


## Correction (planner, 2026-09-19 00:55Z) — cold-cache check, and the Google repos are green

Verify pins with a COLD gitlibs (`rm -rf /tmp/gl && GITLIBS=/tmp/gl clojure -Sforce -Spath`) — a warm local cache hides every dangling pin; CI has none.

- **Agent main (`679aee8`) is already clean**: pins foundation `cc53d69` (main), no `config-cache` reference. The dead pin is only in the RELEASED agent `0e804c0` (0.1.71, deployed) via foundation `1c8e45b` (branch-only). So the agent leg = release agent main (0.1.72) and bump downstream pins; no step port needed.
- isaac-google `a78124b`, isaac-gchat `702a3b0`, isaac-gmail `9ba7a2e`: http → `ad4ba5d` (last http commit with a live foundation pin, pre-tdlz), agent → main `679aee8`, foundation → main, google → main; gmail dropped two steps that isaac-google main now provides (`no outbound HTTP request to … was made`, `the google auth store has access … and refresh …`). Cold-resolve + `bb ci` green locally on all three. They move to http main once the http leg lands.
- Remaining for the worker: the isaac-http leg (`bean/isaac-lsz2` @ c787b7d, 5 feature reds), then release agent 0.1.72 and repin hail/acp/cron/discord/hooks/mcp/claude-code/cli-server/cli-proxy/episodes/worksite/foreman to agent+foundation main, verified cold.

## Worker progress (scrapper@isaac-work-1, 2026-09-19)

Do not land. Do not pin. Verify lands each module branch independently.

### Done this session

- isaac-agent `bean/isaac-lsz2` @ `cc95e9e` (base origin/main@`679aee8`) — foundation → `0b120cc`. Pushed. (prior turn)
- isaac-http/server `bean/isaac-lsz2` @ `097ee33` (base origin/main@`234304e`) — foundation → `0b120cc`, agent → origin/main `679aee8`; comm berth + `*module-index*` bind. `bb spec` 156 green. Pushed. (prior turn)
- isaac-cli-server `bean/isaac-lsz2` @ `87e5a6f` (base origin/main@`4a98732`) — foundation → `e4da6e0` (reachable). Pushed. (prior turn)
- isaac-hail `bean/isaac-lsz2` @ `b020ad1` (base origin/main@`3632bd9`) — foundation `ad0a97b`→`0b120cc`. `bb spec` 168/0. Pushed.
- isaac-cron `bean/isaac-lsz2` @ `f4bbd52` (base origin/main@`01e165e`) — same. `bb spec` 23/0. Pushed.
- isaac-mcp `bean/isaac-lsz2` @ `44c408d` (base origin/main@`8583ebc`) — same. `bb spec` 32 examples, 1 pre-existing failure: `isaac.mcp.client` "returns a timeout error when catalog query is stare" NPE. Main CI already red (isaac-0szr). Pin-only commit. Pushed.
- isaac-hooks `bean/isaac-lsz2` @ `7aca17b` (base origin/main@`55e228d`) — same. `bb spec` 30/0. Pushed.
- isaac-discord `bean/isaac-lsz2` @ `3e735bd` (base origin/main@`21269d4`) — same. `bb spec` 52/0. Pushed. (bean notes genuine feature reds; leave to their own beans)
- isaac-claude-code `bean/isaac-lsz2` @ `4507786` (base origin/main@`bf96e32`) — foundation `ad0a97b`/`0b9ecdf`/`43cf46e` → `0b120cc`. `bb spec` 78/0 (3 pending @real smokes). Pushed.
- isaac-gchat `bean/isaac-lsz2` @ `1cb667b` (base origin/main@`826a768`) — foundation → `0b120cc`. google pin already origin/main `3f35d2c`. `bb spec` 39/0. Pushed.
- isaac-gmail `bean/isaac-lsz2` @ `8d88c05` (base origin/main@`bc42e7a`) — foundation → `0b120cc`. google pin already `3f35d2c`. `bb spec` 17/0. Pushed.
- isaac-google origin/main `a78124b` already pins foundation `b644562` (current origin/main, reachable) + agent `679aee8`. No further commit.
- isaac-acp origin/main already pins foundation `1afd934` (ancestor of origin/main). No further commit.
- AGENTS.md pin rule + verify.md checklist step "Sibling pins reachable from origin/main".

### Pin target

foundation main `0b120ccf68d1ca43b7f66547344796195957ab6e` (ancestor of current foundation origin/main `b644562`).

### Do NOT delete

`bean/isaac-1fwl` / `bean/isaac-t1om` until every listed branch is landed.



## Verify fail (attempt 1, 2026-09-19): isaac-http bb ci features red (4) + unexcepted feature edits; gchat/gmail squash conflict vs later main pins

HEAD (beans): 125cc1db. Working tree: clean.

Do not land remaining branches. Do not complete.

### Already on origin/main (pin-only squash; branches deleted)

- isaac-agent `c3a56a8b6518fbe9d1351c9faa8f1c090d0206b2`
- isaac-cron `de59aa3a29605b3fe6b0dd9c6bc8d58ec1bbe8ec`
- isaac-hail `c44c65490de1898e8d0a3141199001589dcfd224`
- isaac-hooks `0602e9e616625d78fdaa74efd8b08009e2957dc1`
- isaac-discord `6e7e411c0b1f1df20306a970330d1c947ac2b6ab`
- isaac-claude-code `7ff4c350ac1fca7d3440a274082e804136f94e78`
- isaac-mcp `a3977362b82573fdbd6cdfa8e7103e014df87805`
- isaac-cli-server `007da61d029b73b347a003cff47ecae3d87eb49a` (`ISAAC_GIT=1 bb ci` 17 spec / 28 feature green)

google/acp: no lsz2 branch; pins already reachable on main.

### isaac-http FAIL — `bean/isaac-lsz2` @ `097ee33` (base origin/main `234304e`)

`ISAAC_GIT=1 bb ci`: 156 spec / 0 fail; **86 features / 4 fail**. GREEN means full suite. Not landed.

1. `features/http/config.feature` Scenario ":http bind/auth config is valid" — table is only `{:http {:host "0.0.0.0" :port 6674 :auth {:token "marigold"}}}`; agent main requires `defaults.crew`. Expected `[]`, got that error. File **not** in the branch diff — worker did not add the key here.
2. Comm extension "Multiple comm instances of the same :type coexist" — expected event `:comm/activated`, got `:server/hello`.
3. Module activation "Comm slot starts when configured at boot" — expected `:lifecycle/started`, got `:server/hello`.
4. Module activation "Module activation failure surfaces a structured error" — expected module `isaac.http.test-comm`, got `nil`.

These are the ex-isaac-ane7 / agent-main family the bean already named. Pin + berth/`*module-index*` bind did not make them green.

### Feature-file tampering (verify.md §1) — no `## Exceptions`

Branch vs origin/main edits **not** `@wip` removal:

- `features/module/activation.feature` — added `:defaults {:crew "main"}` + `:crew {"main" {}}` to three scenario tables
- `features/module/comm_extension.feature` — added `:defaults {:crew "main"}`

Bean has **no** `## Exceptions` section. Recut assertions/tables only with a planner Exceptions entry, or keep the scenarios and fix the boot/activation path against agent `679aee8`.

### gchat / gmail — squash CONFLICT (left in place)

`git merge --squash bean/isaac-lsz2` conflicts on `bb.edn` / `deps.edn`. Skill: conflict = FAIL, `git reset --hard origin/main`, leave the branch.

Main already has **newer** reachable pins than the leftover branches (do not land — would regress):

| repo | origin/main | leftover `bean/isaac-lsz2` |
|---|---|---|
| isaac-gchat | `702a3b0` agent `679aee8` foundation `b644562` google `a78124b` | `1cb667b` agent `0e804c0` foundation `0b120cc` google `3f35d2c` |
| isaac-gmail | `9ba7a2e` (same newer pins) | `8d88c05` (same older pins) |

Worker: drop or delete those two branches after confirming main is sufficient. Do not rebase the obsolete pin commit onto main.

### Required to re-hand

1. `cd isaac-http && ISAAC_GIT=1 bb ci` green on `bean/isaac-lsz2` (specs + **all** features).
2. Planner `## Exceptions` for any non-@wip feature edits, or revert those edits and fix production/boot.
3. Do not retouch the eight already-landed repos. Do not land gchat/gmail leftover branches.


## Planner note (2026-09-19 01:20Z) — coordination with the worker

- **Google repos are DONE and green on main — do not redo them**: isaac-google `a78124b`, isaac-gchat `702a3b0`, isaac-gmail `9ba7a2e` pin http `ad4ba5d` (pre-tdlz, last http with a live foundation pin), agent main `679aee8`, foundation main, google main; two gmail steps removed that google main now provides. The "gchat/gmail squash conflict" in verify attempt 1 is against these — rebase onto their mains and keep them; move their http pin to http main once the http leg lands.
- isaac-http `bean/isaac-lsz2` @ `097ee33` is the worker's (716d8ec + agent pin); the planner's earlier partial (`c787b7d`: same repins + `auth_cli_spec` seeding `{:defaults {:crew :main}}` + a crew file — the composed schema on agent main requires defaults.crew and set-config refuses an invalid root) is superseded; re-apply the spec seeding if the auth CLI specs are red on your branch. `features/http/config.feature` ":http bind/auth config is valid" needs `defaults.crew` in its config table for the same reason.
- The planner briefly deleted the remote `bean/isaac-lsz2` in isaac-http by mistake at ~01:10Z and restored it from reflog within minutes; if your local push was rejected in that window, `git pull --rebase` and push again.


## Worker repair (scrapper@isaac-work-2, 2026-09-19) — isaac-http re-hand

Do not land. Do not pin. Do not retouch the eight already-landed repos. Do not land gchat/gmail leftover branches.

### isaac-http `bean/isaac-lsz2` @ `bbf3807` (base origin/main@`234304e`)

Pushed. Feature files vs origin/main: **no diff** (no table edits; no Exceptions needed).

`ISAAC_GIT=1 bb spec` 157/0. `ISAAC_GIT=1 bb features` 86/0. `config-bypass-lint` ok. `bb pins` is a local CLI mismatch (`Unknown modules subcommand: pins` from the sibling libexec); CI uses the live `isaac modules pins`.

Fixes vs verify-fail @ `097ee33`:

1. Feature harness stamps `defaults.crew` / `:crew {"main" {}}` on isaac.edn writes so agent `679aee8` schema is satisfied without editing Gherkin tables (`features/http/config.feature` green).
2. `load-server-config-result` **does not** wipe loader `:errors` — only drops `defaults.crew` schema errors after the stamp. Unregistered `:type` still fails boot (`features/config/reconciler.feature:71` green).
3. Restored `:isaac.http/comm` berth (foundation `0b120cc` factory + berth-decl). Premature `:isaac.server/comm` rename made test-comm "berth not declared" and blocked activation/comm_extension. Dual-key contribution dropped; test-comm lives under `:isaac.http/comm` only.

gchat/gmail leftover `bean/isaac-lsz2` branches left in place (main already has newer reachable pins).


## Landed on main (2026-09-19)

main-sha: isaac-agent c3a56a8b6518fbe9d1351c9faa8f1c090d0206b2
main-sha: isaac-cron de59aa3a29605b3fe6b0dd9c6bc8d58ec1bbe8ec
main-sha: isaac-hail c44c65490de1898e8d0a3141199001589dcfd224
main-sha: isaac-hooks 0602e9e616625d78fdaa74efd8b08009e2957dc1
main-sha: isaac-discord 6e7e411c0b1f1df20306a970330d1c947ac2b6ab
main-sha: isaac-claude-code 7ff4c350ac1fca7d3440a274082e804136f94e78
main-sha: isaac-mcp a3977362b82573fdbd6cdfa8e7103e014df87805
main-sha: isaac-cli-server 007da61d029b73b347a003cff47ecae3d87eb49a
main-sha: isaac-http d41cb7dd46f133efd1976bd3106e3e3b5b96a373
main-sha: isaac-server d41cb7dd46f133efd1976bd3106e3e3b5b96a373
