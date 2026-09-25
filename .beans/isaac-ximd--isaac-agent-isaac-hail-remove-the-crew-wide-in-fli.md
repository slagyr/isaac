---
# isaac-ximd
title: 'isaac-agent + isaac-hail: remove the crew-wide in-flight cap — turns serialize per session only, sessions run in parallel'
status: in-progress
type: feature
priority: high
created_at: 2026-09-25T14:54:13Z
updated_at: 2026-09-25T14:54:52Z
---

## Why (Micah, 2026-09-25)

"Time to remove that in-flight limit." A turn must be serialized per
session — two prompts in one session never run at once — but two sessions
must run in parallel. Today `:max-in-flight` is a crew-wide cap that
defaults to **1** when unset (`isaac.session.store.spi/crew-max-in-flight`,
`isaac.hail.delivery-worker/crew-max-in-flight`), so a crew with no setting
serializes every session it owns: on yopp every space, DM and email thread
queued behind one another until the planner set 4 by hand.

## Design

**isaac-agent**
- `can-dispatch?` admits a turn whenever the target session itself is not
  in flight. `crew-max-in-flight` is deleted; `in-flight-count` stays for
  `sessions list --in-flight` and hail.
- Crew schema: `:max-in-flight` becomes `{:type :ignore :validations
  [[:retired? "the crew-wide in-flight cap is gone (isaac-ximd); turns serialize
  per session only"]]}` — a config that still sets it fails validation and
  says why.
- Delivery/attention paths that read `:max-in-flight` from live config (see
  model_reload.feature's note) read nothing.

**isaac-hail**
- `delivery-worker`: `crew-available?` no longer consults a cap; a spawn or
  bind proceeds whenever the bound session is idle. The
  `:crew-at-capacity` skip reason disappears; `:session-in-flight` stays.
- Scenario "a crew at capacity is a named skip reason" is replaced by
  "a busy session on the crew does not gate another session's delivery".

**Deploy note (planner):** remove `:max-in-flight` from zanebot crews
perceptor, prowl, qwen, ratchet, scrapper and from yopp crew yopp before
upgrading, or validation fails.

## Acceptance (baselined: agent concurrency.feature + hail bound_unclaimed.feature)

- [ ] Agent scenario "two sessions on one crew run their turns at the same
  time — no crew-wide cap (isaac-ximd)": a rendezvous tool that only returns
  once 2 calls are in flight is called once from each of two sessions on
  the same crew, and both turns complete. New step: `When the user sends
  "<text>" on sessions "<a>" and "<b>" at the same time via memory comm`
  (dispatch both, then wait for both).
- [ ] Hail scenario "a busy session on the crew does not gate another
  session's delivery — no crew-wide cap (isaac-ximd)".
- [ ] Config with `:max-in-flight` set → validation error naming the key
  and the bean (spec).
- [ ] Existing suspend/cancel/in-flight scenarios green; version bumps in
  both repos; bb spec / bb features / bb lint green in both. Hail pins the
  agent sha that carries the change.

Likely repo scope: isaac-agent (session/store/spi.clj, manifest crew schema,
features/session/concurrency.feature, steps) and isaac-hail
(delivery_worker.clj, bound_unclaimed.feature, deps pin).

feature-baseline: isaac-agent e1c375810961b75b1aaadecf5ca3e982ec2f9aaa
feature-baseline: isaac-hail 2c80a6867ed2179a3f52d884db260d143da1633c
feature-blob: isaac-agent features/session/concurrency.feature fb68b791310039d1b0b9a5dc5ff2ad5b262839c0 15
feature-blob: isaac-hail features/bound_unclaimed.feature 76095baa1969af590039f01e1eb647ed2fbf54eb 46

## Checkpoint (2026-09-25)

Done: isaac-agent branch `bean/isaac-ximd` pushed at `b7ee942` removes the crew cap, retires `:max-in-flight`, and activates the baselined concurrent-session scenario. isaac-hail branch `bean/isaac-ximd` removes delivery capacity gating and activates its baselined scenario; focused `bb spec spec/isaac/hail/delivery_worker_spec.clj` and `bb features features/bound_unclaimed.feature:46` pass.

Next: finish full suites and version/pin work, then gate and land both repos. Agent full `bb spec` passed (1792 examples); its subsequent full `bb features` is red in the unrelated existing `features/session/parallel_tool_batches.feature:79` cancel-mid-batch scenario (868 examples, 1 failure, 1 pending). Resume from `/Users/zane/agents/isaac/work-2/isaac-agent-ximd/features/session/parallel_tool_batches.feature:79` after rerunning to determine flake versus regression; hail delivery implementation resumes at `/Users/zane/agents/isaac/work-2/isaac-hail-ximd/src/isaac/hail/delivery_worker.clj:535`.


## Planner adjustment (2026-09-25, prowl@isaac-plan) — crew-cap scenarios retired on hail main

Conflict stands. Removing the crew cap makes three live hail contracts fail, and a worker may only drop `@wip`. Those contracts are retired on isaac-hail main `d2944e2` (planner commit). Do not restore them.

### Retired (isaac-hail `d2944e2`)

- `features/delivery.feature`: deleted "a delivery for an at-capacity crew is left pending". Header no longer says the worker gates on crew capacity. The two `max-in-flight` setup rows (in-flight pending, serialize-across-ticks) are gone — the key is retired, so a scenario must not set it.
- `features/session-create.feature`: deleted "a create delivery waits when the resolved processing crew is at capacity". The wait-no-sibling scenario no longer sets `max-in-flight`. A busy *matching* session still waits and does not spawn a sibling. That stays.
- `features/bound_unclaimed.feature`: "requeued unbound" is now `@wip` "a bound delivery unclaimed past the stale threshold while its session is genuinely busy stays bound — no crew-wide rebound (isaac-ximd)" (Scenario line 104). A stale bind stays on the busy session. It is not moved to another idle session of the crew. `:rebound-stale` must not fire. Feature preamble matches.

The original ximd scenario is still `@wip` (Scenario line 48). Both `@wip` lines are this bean's.

### Re-baselined (newest lines in force)

    feature-baseline: isaac-hail d2944e2c78aa2331d6e94f2d391cdd0263de27a2
    feature-blob: isaac-hail features/bound_unclaimed.feature 546da6294a59a37d7d5fbbb28d07352e79a80711 48,104
    feature-blob: isaac-hail features/delivery.feature c78cd9b1f4a3965b09a3ee77f6454d0128aa9c66
    feature-blob: isaac-hail features/session-create.feature 3ecc136863c1e2913ffbd06f6443aa688eb53f13

Agent baseline is unchanged (`e1c3758`, concurrency.feature line 15).

### Worker now

1. Rebase both `bean/isaac-ximd` branches onto origin/main. Keep the implementation. On hail, the feature diff against `d2944e2` may only drop the two `@wip` lines (48 and 104). Do not restore the at-capacity scenarios, the `max-in-flight` rows, or the rebound-to-boiler-room contract.
2. `recover-stale-bound!` must not rebind onto `alternate-session` when the bound session is genuinely busy. Leave it bound. Claim it when that session is idle (the false-in-flight path is unchanged).
3. `:max-in-flight` still fails validation and names the key and isaac-ximd. `model_reload.feature` prose that contrasts `:max-in-flight` is a comment, not a contract — leave it, or drop the contrast in a non-scenario line if lint requires. Do not add a scenario for it.
4. Deploy note stands: remove `:max-in-flight` from zanebot crews before upgrade. Planner does that; the worker does not edit `~/.isaac/config`.
5. `parallel_tool_batches.feature:79` is not this bean. Re-run it. If it fails on main too, say so and do not absorb it. If it fails only on this branch, it is in scope.
6. Hail pins the agent sha that carries the change. Land agent, then hail. `bb bean-gate verify isaac-ximd` exit 0 before landing.

This note resets the verify-fail counter.

## Gate conflict (2026-09-25, scrapper@isaac-work-1)

`bb bean-gate verify isaac-ximd --dir isaac-agent=/Users/zane/agents/isaac/work-1/isaac-agent-ximd-land --ref isaac-agent=b181ef2bacfc7ebe19ac72554a47645c393e2c47 --dir isaac-hail=/Users/zane/agents/isaac/work-1/isaac-hail-ximd-scrapper --ref isaac-hail=4409449` exits 1. The two ximd `@wip` lines are removed exactly as directed, but the newest top-level, line-less `feature-blob: isaac-hail features/delivery.feature c78cd9b1f4a3965b09a3ee77f6454d0128aa9c66` makes every pre-existing `@wip` in delivery.feature this bean's live contract. The gate reports 14 `delivery.feature` scenarios still carry `@wip` (isaac-9azm and related beans). The permitted hail feature diff against d2944e2 only removes the two bound_unclaimed `@wip` lines, so those 14 tags cannot be removed by this worker. Rebaseline/correct the delivery blob selector.

feature-baseline: isaac-hail d2944e2c78aa2331d6e94f2d391cdd0263de27a2
feature-blob: isaac-hail features/bound_unclaimed.feature 546da6294a59a37d7d5fbbb28d07352e79a80711 48,104
feature-blob: isaac-hail features/delivery.feature c78cd9b1f4a3965b09a3ee77f6454d0128aa9c66
feature-blob: isaac-hail features/session-create.feature 3ecc136863c1e2913ffbd06f6443aa688eb53f13


## Planner adjustment (2026-09-25, prowl@isaac-plan) — delivery blob is not this bean's scenarios

The line-less `feature-blob` for `features/delivery.feature` made every `@wip` scenario in that file this bean's (`live-failures`: no lines means every `@wip` block must lose `@wip`). Those 14 scenarios belong to isaac-9azm and others. This bean did not touch them. The worker was right not to drop those tags.

The delivery and session-create blobs were recorded only so the retired at-capacity scenarios stay deleted. They are not this bean's scenarios. Dropped from the in-force contract. Do not add them back without scenario line numbers, and do not name a line that is another bean's `@wip`.

Agent landed `b181ef2`. The concurrency scenario is no longer `@wip` and moved from line 15 to line 14. Re-baselined to that sha so the gate checks the landed tree, not `e1c3758`.

### In force

    feature-baseline: isaac-agent b181ef2bacfc7ebe19ac72554a47645c393e2c47
    feature-blob: isaac-agent features/session/concurrency.feature 0f7d52d7e97788726a94d346c6f490d821723c1a 14
    feature-baseline: isaac-hail d2944e2c78aa2331d6e94f2d391cdd0263de27a2
    feature-blob: isaac-hail features/bound_unclaimed.feature 546da6294a59a37d7d5fbbb28d07352e79a80711 48,104

### Worker now

1. Rebase `bean/isaac-ximd` (hail) onto `d2944e2` if not already (`4409449`). Keep the implementation and the agent pin `b181ef2`. Feature diff may only drop the two `@wip` lines (bound_unclaimed Scenario lines 48 and 104).
2. Do not drop `@wip` on `delivery.feature`. Those scenarios are not this bean.
3. Agent is already on main `b181ef2`. Do not re-land it. Record `main-sha: isaac-agent b181ef2bacfc7ebe19ac72554a47645c393e2c47` when hail lands.
4. `bb bean-gate verify isaac-ximd` exit 0, then land hail.

This note resets the verify-fail counter.

feature-baseline: isaac-agent b181ef2bacfc7ebe19ac72554a47645c393e2c47
feature-blob: isaac-agent features/session/concurrency.feature 0f7d52d7e97788726a94d346c6f490d821723c1a 14
feature-baseline: isaac-hail d2944e2c78aa2331d6e94f2d391cdd0263de27a2
feature-blob: isaac-hail features/bound_unclaimed.feature 546da6294a59a37d7d5fbbb28d07352e79a80711 48,104

## Landed on main (2026-09-25)

main-sha: isaac-agent b181ef2bacfc7ebe19ac72554a47645c393e2c47
main-sha: isaac-hail 00d9d178f07fe41d03785ac573a30b714a3e800b
main-sha: isaac 2477b43590417a99a8da0076cc56d3066374306c
