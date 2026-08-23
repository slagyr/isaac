---
# isaac-bh17
title: 'Live scene sealing 5: topic-drift trigger, size-cap backstop, (cont) marks'
status: todo
type: task
created_at: 2026-08-23T15:51:25Z
updated_at: 2026-08-23T15:51:25Z
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
