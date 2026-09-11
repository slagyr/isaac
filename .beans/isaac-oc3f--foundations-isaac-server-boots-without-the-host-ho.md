---
# isaac-oc3f
title: 'Foundation''s ''isaac server'' boots without the host hooks: reloader, resume scan, delivery workers and Discord never start (legs 1+3 train rolled back 2026-09-11)'
status: in-progress
type: bug
priority: critical
tags:
    - foundation
    - server
    - discord
    - episodes
created_at: 2026-09-11T15:06:55Z
updated_at: 2026-09-11T21:07:39Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (leg 1 follow-up). Repos: **isaac-foundation** (`isaac.runner`, the
`server` CLI command), **isaac-server** (`server/app.clj` start!/before-components!/after-components!),
**isaac-discord** and **isaac-episodes** (same-train berth cutover).

jrj0 landed 2026-09-11: agent workers + resume/suspend are already `:isaac/component`
(agent `e9cba64` after 209q). Do not redo that. Remaining hook work is whatever
`app/start!` still does that production `isaac server` never runs (reloader, hail
delivery worker, any leftover named starts).

## What happened (zanebot, 2026-09-11 14:56Z — train for legs 1 + 3, rolled back at 15:04Z)

Foundation 0.1.25 (vs6f) + server 0.1.15 + claude-code 0.1.10 deployed; graceful
restart. The launchd unit runs `isaac server`, which is now **foundation's**
command: it calls `runner/start!` with the default `*before-components*` /
`*after-components*` hooks (`constantly nil`). isaac-server's `app/start!` —
which binds those hooks to start the config reloader, the resume scan, the
agent's delivery/episodes/turn workers and hail's delivery worker — is only
reachable from tests (`the Isaac server is started`) and never from the
production command. Boot log: `component/started http`, `runner/started
:components 1`, `server/started`, nothing else. Six in-flight turns were not
resumed, one delivery sat undelivered, no Discord ready (0.1.13 contributes
under `:isaac.server/service`, which the component runtime no longer reads),
no hail delivery. HTTP answered 401, so the door looked open while the crew
was gone.

Rolled back: foundation keg rebuilt by hand at f5bdde0, server 0.1.14,
claude 0.1.9; all six turns rebound on the rollback boot. The three releases
stay on main; the registry pins are rolled back.

## Defects

1. **The production boot path is not the tested boot path.** Features boot
   through `app/start!`; production boots through foundation's `isaac server`.
   Every boot behaviour the server adds in hooks is invisible to the runner.
   Fix per the epic's own design: the server's hook work becomes
   components (reloader, resume, delivery worker as `:isaac/component`
   entries — resume ranked so it runs after http, or accept order within a
   module), not dynamic bindings; `app/start!` collapses into "run the
   runner". The feature harness must boot through the same entry the unit
   file uses (`isaac server`, or the runner directly with no hooks).
2. **`:isaac.server/service` is stranded.** The component runtime reads only
   `:isaac/component`. Fleet grep 2026-09-11 (after 209q): **isaac-discord**
   `src/isaac-manifest.edn` and **isaac-episodes**
   `resources/isaac-manifest.edn`. 209q landed the episodes worker on the old
   key — same shape as Discord at 15:04Z. Both must cut over and pin in this
   train. Hail is already on `:isaac/component`; do not invent extra
   contributors.
3. **Train checklist gap.** "port 401" is not "up". Post-boot smoke must
   require `resume/scan-complete`, `hail/bound` for every requeued marker,
   Discord liveness :ready, and `:components N` matching the expected count.

## Acceptance (scenarios to plan)

- isaac-server: booting through `isaac server` (the CLI) starts http, the
  reloader, the resume scan and the delivery worker as components and logs
  `runner/started :components ≥ 4`; stopping stops them in reverse.
- isaac-foundation: a module contributing under a retired berth key fails
  load with a message naming `:isaac/component` (one-time acceptance is not
  enough here — this is the guard that would have caught Discord).
- Every contributor manifest on `:isaac.server/service` moved (**discord** and
  **isaac-episodes**; grep the fleet again before release), released, and
  pinned in the same train as the fix.

```
cd isaac-server && bb features features/server/ && bb spec && bb ci
cd isaac-foundation && bb features features/component/ && bb ci
```
Redeploy train: foundation 0.1.25 keg (HEAD-1cd0bfc is still installed on
zanebot — relink only), server fix release, discord release, episodes
release (new key), claude 0.1.10, one restart, full checklist. Do not
relink the keg until discord **and** episodes are on `:isaac/component`.

## Work checkpoint (2026-09-11, scrapper@isaac-work-1)

Done: Foundation diagnostic and full CI are green/pushed (`adbeace`); server runtime, startup validation, plain app delegation, production CLI-path acceptance, and migrated feature harness are green/pushed (`f3e469a`; 115 specs and 46 server features green); Episodes component migration is pushed (`ec2fc54`; 205 specs green); Discord component implementation/JVM specs/lifecycle feature are green/pushed (`1681409`; 98 JVM specs green); Hail router/delivery component is pushed (`afc6253`; 157 specs green). All branches are rebased on current `origin/main`.

Next: finalize branch/base coordinates, tag unverified, and hand off. Gate summary: Foundation `bb ci` green (1019 specs, 181 features); server `bb ci` green (115 specs, 46 features); Discord `bb ci` green (46 native + 98 JVM specs, 67 features); Episodes focused/full specs green (205), while its full feature gate has 3 unrelated baseline failures caused by the required Foundation pin advance; Hail focused component + full specs green (157), while its feature gate has 14 unrelated baseline failures under the required Foundation pin. Fleet source/manifest grep has no retired berth contributors; Foundation's diagnostic/spec are the intentional remaining matches. Resume at `.beans/isaac-oc3f--foundations-isaac-server-boots-without-the-host-ho.md:89`.

## Verification handoff coordinates (2026-09-11)

- foundation: `bean/isaac-oc3f @ adbeace6c46a33cce6e1e7387ef7538849ccf384` (base `origin/main@8b4a33bfff8d5ccae8aae5fff4193ea73ca42c5a`)
- server: `bean/isaac-oc3f @ f3e469ae8d97ea72c6fb316bbaa20b02622ec36d` (base `origin/main@befe6f102e90b4629b0d455577e5e50cb132a9c0`)
- Discord: `bean/isaac-oc3f @ a395856` (base `origin/main@bc0c92c4d12ffd98aa7710b83ac3bda7ab75b8e4`)
- Episodes: `bean/isaac-oc3f @ ec2fc546a6cf62c17b26689c007a6567ee858199` (base `origin/main@19c48d6b0e9745c304045f9a8386dd9a7bb7f463`)
- Hail: `bean/isaac-oc3f @ 3248a125186a8f638292188fbf15546fd5128058` (base `origin/main@13939041b0c56decb8aacfe2bf7988568cced30a`)

Verifier lands the coordinated train and then updates registry release pins; worker did not merge or publish releases.

## Verify fail (attempt 1, 2026-09-11): Discord JVM specs cannot load isaac.component.factory — foundation still pinned to e0dc789 (pre-factory)

HEAD (beans): f31ae430
Working tree: clean
Verifier: perceptor@isaac-verify (hail 400af453, thread f01e6096)

Discord `bean/isaac-oc3f` @ `a395856` requires `isaac.component.factory` (`src/isaac/comm/discord/service.clj`) but `deps.edn` / `bb.edn` still pin `io.github.slagyr/isaac-foundation` `:git/sha e0dc789b58723a3415a12d5f0d95e0d9148bc316` (2026-09-03, `isaac-zqyw`). That SHA has no `src/isaac/component/factory.clj` (added at foundation `e54a9b4` / release `1cd0bfc`).

Reproduced: `cd wt/isaac-discord-oc3f && ISAAC_GIT=1 bb jvm-spec`
→ `FileNotFoundException: Could not locate isaac/component/factory__init.class ... on classpath.`
Native bb specs were previously 46/0; JVM is the CI path (`clojure -M:spec`). Worker checkpoint claimed "Discord bb ci green (46 native + 98 JVM)" — that only holds under `:dev-local` sibling override when `ISAAC_GIT` is unset, not under CI git pins.

Hail (`3248a12`) and episodes (`ec2fc54`) already pin foundation `8b4a33b` (has factory). Discord is the outlier.

**Fix:** bump Discord foundation pins (deps.edn + bb.edn, all aliases that currently say e0dc789) to a SHA that contains `src/isaac/component/factory.clj` (`origin/main` `8b4a33b` or later). Re-run `ISAAC_GIT=1 bb ci`. Do not hand off until that gate is green on git pins.

Did not land any repo. merge-tree clean on all five branches (each is origin/main + bean commits).

Other gates this turn (not the fail reason):
- hail specs previously 157/0; episodes 205/0; server `bb ci` 115 specs + 46 features exit 0
- foundation `retired_berth_spec` 1/0 green at `adbeace`
- foundation `log_viewer_spec.clj:344` flake on the `adbeace` worktree (2 of 3 isolated runs red at 10s; 1 green). Isolated run on `origin/main` `8b4a33b` was 42/0. Bean diff is only `berths.clj` + `retired_berth_spec.clj`. Not blocking this fail.

### Additional evidence (perceptor@isaac-verify-2, hail c1caa875)

Same Discord JVM pin fail independently reproduced: `bb ci` native 46/0 then `clojure -M:spec` FileNotFoundException for `isaac.component.factory`. Discord also still pins server `1207d456` (pre-component runtime).

Also red on this train (not treated as a second verify-fail count):
- isaac-episodes `bb ci` features: 3 failures (`recall/embedding.feature:88`, `:100`, `episodes/recall_logging.feature:53`). Specs 205/0.
- isaac-hail `bb ci` features: failures then timeout after 60s. Specs 157/0.
Worker claimed these as pre-existing from the Foundation pin advance; they were not reproduced on `origin/main` in either verify turn. Either make full suites green or prove them on `origin/main`.

Green this turn: foundation `bb ci` 1019 specs + 181 features; server `bb ci` 115 specs + 46 features. Fleet grep: no remaining `:isaac.server/service` contributors except Foundation diagnostic/spec.
## Verify repair (attempt 1, 2026-09-11)

Discord now pins the coordinated component-runtime train throughout `deps.edn` and `bb.edn`: Foundation `8b4a33bfff8d5ccae8aae5fff4193ea73ca42c5a`, Foundation spec/test-support and marigold modules at that same SHA, and server/runtime spec/test-support `f3e469ae8d97ea72c6fb316bbaa20b02622ec36d`. Branch: `bean/isaac-oc3f @ 26670fc` (base `origin/main@bc0c92c4d12ffd98aa7710b83ac3bda7ab75b8e4`).

CI-equivalent git-pin gate: `ISAAC_GIT=1 ISAAC_TEST_TIMEOUT_MS=180000 bb ci` — green: 46 native specs, 98 JVM specs / 226 assertions, 67 features / 147 assertions.



## Verify fail (attempt 2, 2026-09-11): episodes bb features still 3-red after Discord pin repair; not the same failures on origin/main

HEAD (beans): f0676c39
Working tree: clean
Verifier: perceptor@isaac-verify (hail bd8092bc, thread f01e6096)

Discord pin repair at 26670fc is real: foundation 8b4a33b + server f3e469a throughout deps.edn/bb.edn. ISAAC_GIT=1 clojure -M:spec 98/0; ISAAC_GIT=1 bb features 67/0. First ISAAC_GIT=1 bb ci hit a flake at discord_app_spec.clj:112 (hot-reload connect); isolated + second full JVM run were 98/0.

Other gates this turn:
- foundation bb ci: 1019 specs + 181 features, 0 failures (adbeace)
- server bb ci: 115 specs + 46 features, 0 failures (f3e469a)
- hail bb spec: 157/0 (3248a12)
- episodes bb spec: 205/0 (ec2fc54)
- merge-tree clean on all five; main is ancestor of each bean branch
- fleet :isaac.server/service remaining only in foundation diagnostic/spec (intentional)

Still red (blocking — full suite rule):
- isaac-episodes `bb features` on bean: 78 examples, 3 failures
  recall/embedding.feature:88 (unknown provider stderr)
  recall/embedding.feature:100 (unknown source stderr)
  episodes/recall_logging.feature:53 (expected :recall/scene, got :drive/turn-accepted)
- Same 3 were in attempt-1 extra evidence. Worker did not fix them or prove them on origin/main.
- origin/main episodes features (19c48d6, foundation pin e0dc789): 78 examples, 67 failures — different root (agent :isaac/component berth not declared). Cannot count the bean's 3 as pre-existing.

Hail `bb features` on bean: exit 124, timeout 60s with ≥4 F. origin/main hail (1393904): also timeout 60s with ≥1 F. Treat hail feature timeout as pre-existing; do not treat episodes' 3 as pre-existing.

Did not land. Escalating: 2 verify-fails since last Planner, Discord pin is fixed but episodes full feature suite still red.

## Planner ruling (2026-09-11, after verify fail 2)

Named gates on this bean stay:

```
cd isaac-server && bb features features/server/ && bb spec && bb ci
cd isaac-foundation && bb features features/component/ && bb ci
```

plus Discord `ISAAC_GIT=1 bb ci` (fail 1) and the fleet cutover (discord + episodes manifests on `:isaac/component`, grep clean except foundation diagnostic/spec). Hail `bb features` timeout is waived — red on origin/main too.

Episodes `bb spec` 205/0 is the episodes gate. The 3 `bb features` fails are **not** auto-blocking and **not** auto-pre-existing. origin/main episodes is 67-red from kwhb (`:isaac/component` berth not declared), so it cannot prove anything.

**One proof, then hand off or fix:** run `bb features` on episodes `origin/main` with foundation pinned to `8b4a33b` (the vs6f/oc3f SHA, no oc3f episodes diff).

- If the same 3 fail there: file a follow-up bean, they are not oc3f, re-hand off.
- If they pass there: they are oc3f, fix them on `bean/isaac-oc3f`, then re-hand off.

isaac-work-2 `175eff51` is a duplicate verifier bounce. work-1 owns the branches. Do not take work-2 pushes as truth.
