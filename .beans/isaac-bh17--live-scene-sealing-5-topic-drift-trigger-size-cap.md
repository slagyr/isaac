---
# isaac-bh17
title: 'Live scene sealing 5: topic-drift trigger, size-cap backstop, (cont) marks'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-23T15:51:25Z
updated_at: 2026-08-24T03:41:18Z
parent: isaac-51xy
---

Bean 5 of isaac-51xy: scenes seal DURING episodes, not only at close. Decisions below (2026-08-23, Micah).

## Decisions (2026-08-23, Micah + planning session)

1. **Two seal triggers; topic drift is PRIMARY (Micah: arguably the more important motivation), size cap is the backstop.** Size: unsealed tail reaches :size-cap (default 80 messages, same as migration spans) -> segment the tail. Drift: after each turn, embed the new distilled exchange and compare (cosine) to the ROLLING OPEN-SCENE VECTOR (epic decision 6, now real): below :drift-threshold with tail >= :min-tail -> segment early. Knobs: :episodes {:seal {:size-cap 80 :drift-threshold <d> :min-tail <n>}}.
2. **The drift trigger is a HINT; segmentation is the judge.** False positives are absorbed structurally: if the segmenter returns a single scene, seal-all-but-last seals nothing — cost of a false trigger is one wasted gist call, no bad seal. No consecutive-K machinery needed.
3. **Seal all but the last scene** (the trailing scene is the live conversation). Single-scene output at the HARD size cap seals entirely (cap must bound memory; fresh open scene starts). Sealing runs POST-REPLY (after response delivery, before process exit; 1-2s invisible). Seal failure: loud log, turn untouched, next trigger retries (idempotent over the tail).
4. **Rolling open-scene vector**: running mean of distilled-exchange embeddings since last seal, persisted on the episode record (quantized ints) so CLI processes survive; updated post-turn (~100ms nightbird). Reset on seal.
5. **(cont ...) marks are IN (Micah: very important).** Segmentation line format gains `(cont <first>-<last>)` after the ordinal: parser stores :continues on the sealed scene, RESOLVED to scene ids at seal time. Scope rule: ordinals are span-local, so :continues resolves only within the same seal batch; a (cont) pointing at the still-open last scene is dropped with a log. Cross-episode/cross-batch arc joins stay phase 3 — this bean only collects links. Prompt instruction added alongside routine/~ rules.
6. **Sealed scenes index immediately** (same batch path as index-at-close from isaac-h5dk). **No-embedding tier: drift trigger inert (no vectors), size-cap sealing still works** — tier consistency with h5dk decision.

## Scenarios (2026-08-23, committed @wip)

features/episodes/live.feature +5: size-cap seals mid-episode (trailing scene stays open; sealed scene indexed — recall-CLI proof); drift seals under the cap (identical fixture to size-cap scenario, config the only variable — controlled experiment); false drift trigger absorbed (single-scene output seals nothing, exit 0); (cont 1-2) resolves to a scene id in :continues frontmatter (braided wine/regatta/wine fixture; trailing scene stays open); no-embedding tier (drift configured but inert — the trap: embedding for drift would crash; cap seals, nothing indexed).
features/episodes/migrate_session.feature: prompt-contract scenario revised @wip — lookaheads add \(cont and resumes (shared prompt: migration collects arc links too).

## Step ledger

| step | status |
|---|---|
| all steps | reuse — one exception below |
| **that episode has no sealed scenes** | **NEW — zero scene .md files under the remembered episode's dir (exact-count matcher cannot take an empty table)** |
| that episode has scenes matching: | reuse — `continues` column rides the generic matcher (empty cell = key absent; regex cell matches stringified id), same as xl6h's routine column |

## Production notes

- New post-turn seam: `maybe-seal!` in episodes/lifecycle.clj, called after reply delivery on episode-crew turns (CLI: before process exit). Order: update rolling vector -> check triggers (drift needs embedding + tail >= :min-tail; cap always) -> segment tail (segment-span! reuse) -> seal all but last (seal-scenes reuse; hard-cap single-scene seals entirely) -> index batch (h5dk path) -> reset rolling vector.
- Rolling open-scene vector: running mean of distilled-exchange embeddings since last seal, quantized ints persisted on episode.edn; absent when no :embedding.
- Parser: BOUNDARY_LINE gains optional `\(cont <a>-<b>\)` group after the ordinal colon; resolve-ordinals carries :continues-ordinals; seal resolves to scene ids within the batch. store.clj SCENE_FRONTMATTER_KEYS + :continues.
- Config schema: :episodes :seal {:size-cap :drift-threshold :min-tail} contributions.
- SPEC OBLIGATIONS: (cont) targeting the still-open trailing scene -> mark dropped with loud log; drift cosine math on quantized vectors; rolling-mean update/reset; seal failure -> loud log, turn unharmed, retry next trigger (idempotent tail); hard-cap single-scene full-seal rule.

## Acceptance

Remove @wip; these pass and previously-green suites stay green:

```
bb features features/episodes/live.feature
bb features features/episodes/migrate_session.feature features/episodes/index.feature features/recall/query.feature features/recall/live_tools.feature
bb spec spec/isaac/episodes spec/isaac/recall
```

DEPENDENCY: isaac-h5dk (index-at-close batch path, recall live plumbing) — do not start before h5dk lands. Field check on zanebot (recorded here): pilot crew, multi-topic sitting seals mid-episode; recall finds a scene from the STILL-OPEN episode; a (cont) link appears in a live-sealed scene.


## Worker notes (scrapper@isaac-work-2)

Live sealing shipped on isaac-agent **9f6c929**.

- maybe-seal! post-reply: rolling open-scene vector → drift/cap triggers → segment tail → seal all-but-last (hard-cap single-scene seals entirely) → index-crew! → reset vector. Failure: :episodes/seal-failed, episode unharmed.
- prompt_cli calls maybe-seal! after a successful printed reply (CLI process exit). Bridge dispatch seals after a successful non-CLI episode-crew turn.
- Parser BOUNDARY_LINE optional (cont a-b); in-batch resolve to scene ids; still-open last scene dropped + :episodes/cont-dropped.
- SCENE_FRONTMATTER_KEYS + :continues. Distill prompt mentions (cont) + resumes.
- Manifest :episodes :seal {:size-cap :drift-threshold :min-tail}.
- Grover stub: short strings keep the documented 4-dim contract; longer wine/regatta fixture texts are orthogonal so drift can fire.
- New step: that episode has no sealed scenes.
- @wip dropped on 5 live.feature scenarios + migrate prompt-contract.

Suites: bb features live.feature 18/0; migrate+index+query+live_tools 32/0; bb spec spec/isaac/episodes spec/isaac/recall + prompt_cli_spec 178/0.
