---
# isaac-7gjs
title: 'Remove :max-request-tokens: chunk compaction by the context window'
status: in-progress
type: task
priority: high
tags:
    - agent
    - compaction
created_at: 2026-09-15T17:12:15Z
updated_at: 2026-09-15T17:31:52Z
---

## Problem

`:compaction {:max-request-tokens N}` (default 32000) caps the size of each compaction summary request independently of the context window (`src/isaac/session/compaction.clj:478–479`: `chunk-window (min context-window request-cap)`; defaults in `src/isaac/session/context.clj:72` and `:173`). The name hides that it only affects compaction.

On a 1.05M-window model it forces a ~330K-token history into 12–13 summary requests run one after another. Every `isaac-work-1` compaction on zanebot took 21–27 min (09-11 → 09-15: 26.5, 23, 23, 23, 24, 21.5, 21.6 min; each `:chunk-count 12–13 :budget 32000`). Unchunked compactions of similar size finished in 1–2.5 min. On 2026-09-15 the worker on isaac-ejj3 lost 21.6 of 52 minutes to one compaction.

## Why it is unnecessary

- It came from isaac-jgng (2026-09-04): ChatGPT summary requests sized to the window sat silent and died "closed" after ~15 min. The actual cause was isaac-6zk5 — the SSE reader had no idle-stall timeout. That is fixed (`:stream-idle-timeout-ms`, default 90s; zanebot 300s), and summaries run at fixed effort 2.
- The context window already bounds the request. The default `:rubberband` strategy compacts the whole history once the gauge reaches `threshold × window` (0.8), so the summary input is ≤ ~80% of the window, leaving ~20% for the summary prompt and output. The chunker already splits when the summary prompt exceeds the window (`needs-chunking?` checks `context-window`).
- The retry-at-half-size on a stalled/closed chunk (isaac-jgng) stays; that is the real safety net.

## Decisions

- Decision (2026-09-15, Micah): remove `:max-request-tokens`. Chunk against the context window only. Clean cutover: the key hard-rejects in compaction config (schema `src/isaac/session/compaction_schema.clj:12`, policy keys `context.clj:59`), no alias.

## Scope / ripple

- isaac-agent: `compaction.clj` (request-cap, chunk-window, needs-chunking?), `context.clj` (defaults, policy keys), `compaction_schema.clj`, `resources/isaac-manifest.edn` (four `:max-request-tokens` schema entries at ~306/474/543/697).
- Specs/features that pin the cap: `features/session/compaction_requests.feature` (description line 4, row `compaction.max-request-tokens | 670` at ~79), `spec/isaac/session/compaction_spec.clj` (including "chunks a 90k-token history under max-request-tokens 32000 on a 278k window", ~922), `compaction_schema_spec.clj`, `context_spec.clj`. Rewrite chunking specs to chunk by window; delete cap-specific ones.
- **zanebot config:** `~/.isaac/config/models/gpt.edn` currently carries `:compaction {:max-request-tokens 400000}` (added 2026-09-15 as a stopgap; backup `gpt.edn.bak-20260915-maxreq`). The new runtime will reject it, so remove that line in the same script as the restart.

## Scenarios

Scenarios approved 2026-09-15 (Micah). Committed `@wip` in isaac-agent `515a40b`; no new steps.

`features/session/compaction_requests.feature`
- `:140` a history that fits the window is summarized in a single request (isaac-7gjs) — **new**
- `:168` a summary prompt larger than the window is compacted in chunks that fit the window (isaac-7gjs) — **replaces** `:73` "a history under the window but over the request cap is compacted in chunks"

At landing:
- Delete the scenario at `:73` (it configures `compaction.max-request-tokens`).
- Remove `@wip` from `:140` and `:168`. The `context-window 700` / `last-input-tokens 600` figures in `:168` were sized from the old 670-token cap splitting that history into 3 chunks; retune them to the estimator if the chunk count differs, keeping the assertion (3 chunks, merged summary).
- Feature description: title drops "a size cap"; replace "never larger than compaction.max-request-tokens (default 32k) regardless of the model window" with "never larger than the model's context window".
- The two effort scenarios and "a dropped summary request is retried at half size" stay unchanged.

## Acceptance

```
ISAAC_GIT=1 bb features features/session/compaction_requests.feature
bb ci
```

Trust `examples, 0 failures` plus the unwrapped exit (`clojure -M:features …` if in doubt).

One-time checks (not scenarios):
- `git grep max-request-tokens -- src resources spec features` in isaac-agent is empty.
- `{:compaction {:max-request-tokens 1}}` in a model config fails `isaac config validate`.
- After the train: zanebot `models/gpt.edn` no longer has the `:compaction {:max-request-tokens 400000}` line (removed in the restart script), and the next `isaac-work-1` compaction logs `:chunk-count 0` (or no chunk plan) and completes in minutes.

## Worker checkpoint (2026-09-15, plan session)

Done: isaac-agent `bean/isaac-7gjs` @ `b3d309f` (from `origin/main` `515a40b`). `:max-request-tokens` removed from `compaction.clj` (chunk plan and `needs-chunking?` use the window), `context.clj` (policy keys, static and code defaults), `compaction_schema.clj`, and all four manifest compaction schemas. Specs updated (resolved policies drop the key; the 90k-history spec now expects one request on a 278k window). `compaction_requests.feature`: old cap scenario deleted, new scenarios un-`@wip`, description updated. The chunking scenario's window was retuned 700 → 670 (700 planned 2 chunks; 670 plans the asserted 3).

Gates: baseline `bb ci` 1610 specs / 754 features green; branch `bb ci` 1610 specs / 755 features, 0 failures, exit 0. `git grep max-request-tokens -- src resources spec features` is empty.

**Acceptance gap — needs Micah:** `{:compaction {:max-request-tokens 1}}` in a model config does NOT fail validation; it is silently dropped (no error, no warning). Probe against the branch: errors `[]`, warnings `[]`; control `{:compaction {:threshold 5.0}}` does error (`models.harbor.compaction.threshold`), so compaction values are validated but unknown compaction keys are not reported. Cause is in isaac-foundation `src/isaac/config/warnings.clj`: entity kinds (`models`, `crew`, `providers`, …) get a shallow unknown-key scan (`collect-unknown-key-warnings`, one level), only static config tables get the recursive `nested-unknown-key-warnings`, and all unknown-key findings are warnings, not errors. The "hard-reject" decision cannot be met inside isaac-agent.

**Deploy coupling — needs Micah:** isaac-agent `main` also carries `7ba21ce` (isaac-ejj3: built-in `:claude` template removed; `{:type :claude}` now fails validation). Releasing agent from `main` requires, in one restart: pin agent + claude-code `2a9023f`; zanebot `isaac.edn :modules` ids `:isaac.llm.claude` → `:isaac.provider.claude-code` and `:isaac.episodes` → `:isaac.session.episodes`; zanebot `providers/claude.edn` → `{:type :claude-code}`; remove the `gpt.edn` compaction line; yopp `providers/claude-code.edn` → `{:type :claude-code}`.

Next: on Micah's answers — (a) acceptance for the removed key, (b) deploy path — hand to verification, squash-merge, release, and run the train.

## Decisions (2026-09-15, checkpoint follow-up)

- Decision (2026-09-15, Micah): a leftover `:max-request-tokens` key is accepted as silently ignored. The one-time acceptance check "`{:compaction {:max-request-tokens 1}}` fails `isaac config validate`" is replaced by "that key has no effect: the resolved compaction policy never contains it and chunking uses the window". Reporting stale nested keys is a separate foundation bean.
- Decision (2026-09-15, Micah): combined deploy — this release ships with isaac-ejj3's agent and claude-code changes and the zanebot module-id renames in one restart script (see "Deploy coupling" above), with rollback pins ready and the full post-restart checks.
