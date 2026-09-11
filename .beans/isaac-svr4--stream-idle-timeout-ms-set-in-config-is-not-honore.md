---
# isaac-svr4
title: 'stream-idle-timeout-ms set in config is not honored by hail turns: stalls still cut at the 90 s default'
status: draft
type: feature
priority: high
created_at: 2026-09-06T20:08:36Z
updated_at: 2026-09-08T23:08:22Z
---

Repo: isaac-agent (6zk5 follow-up; `src/isaac/llm/http.clj` resolve-idle-timeout-ms, `charge.clj` ensure-provider, `config/resolve.clj` model-override-provider-opts, `llm/api/openai/shared.clj` llm-http-opts).

## Evidence (zanebot, agent 0.1.48/0.1.49, 2026-09-06)
- 19:14Z: `~/.isaac/config/providers/grok.edn` → `{:api "responses" :type :grok :stream-idle-timeout-ms 300000}`; `:config/reloaded` at 19:14:25. `isaac config get providers.grok` shows the key.
- 19:19Z: `isaac.edn` `:defaults {:stream-idle-timeout-ms 300000 …}` (the key `charge/ensure-provider` selects explicitly); reloaded 19:19:48 and 19:20:16; `config validate` OK.
- Hail turns after both reloads still stall at the default: `:llm/stream-stalled :elapsed-ms 90078` (tono-work-1, 19:17:53, 40 KB received) and `:elapsed-ms 90055 :bytes-received 0` (isaac-work-2, 20:05:42). Sessions on crew scrapper, model grok-4-6, provider grok (responses API).
- Code path read by the planner: delivery_worker reads the snapshot per tick → `charge/build {:config cfg}` → `ensure-provider` merges `(select-keys defaults [:stream-idle-timeout-ms])` + resolved provider cfg → `make-provider` passes the whole cfg to the factory → `ResponsesAPI` keeps cfg → `shared/llm-http-opts` forwards `:stream-idle-timeout-ms` → `http/post-sse!` `resolve-idle-timeout-ms opts`. Every link looks right on paper, yet the observed timeout is 90 s. Suspects: `drive/turn.clj` `with-turn-config` (rebuilds the provider from `api/config` + overrides), config normalization dropping unknown provider keys, or the compaction/summary request path building its own provider without the merge. The :llm/stream-stalled event does not log the resolved idle-ms or provider — add both.

## Required
1. A spec that builds a charge from a config with `:defaults {:stream-idle-timeout-ms N}` and with a provider-level key, runs a (grover-simulated) stream that goes silent, and asserts the stall fires at N — through the SAME path the delivery worker uses (`charge/build` → `turn/run-turn!`), not a unit call on http.clj.
2. `:llm/stream-stalled` logs `:idle-timeout-ms` and `:provider`.
3. Whichever link drops the key is fixed.

Until then grok reasoning turns >90 s silent are deferred and re-run from scratch (16 tool calls redone on tono-work-1 at 19:12).



## Re-scoped (planner, 2026-09-08 23:10Z)
`:llm/stream-stalled :elapsed-ms 300018 :bytes-received 41984` on isaac-work-1 at 23:06Z — the configured 300 s IS honored now. The value was set at 19:14/19:19Z and hot-reloaded, but stalls at 19:17, 20:05 still used 90 s; the server has been restarted several times since (20:0x for agent 0.1.52, hail 0.1.16, claude 0.1.7). So the defect is narrower than 'not honored': **a change to stream-idle-timeout-ms does not take effect on hot reload — only after a restart.** Likely the provider (and its http opts) is built once per session/charge and cached, or the delivery worker's cfg for in-flight bindings is stale. Requirement 1 (spec through the delivery-worker path) stays; add: a config reload between two turns changes the idle timeout the second turn uses (no restart). Requirement 2 (log :idle-timeout-ms on the stall event) stays.
