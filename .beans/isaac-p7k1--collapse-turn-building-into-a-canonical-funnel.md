---
# isaac-p7k1
title: Collapse turn-building into a canonical funnel
status: in-progress
type: task
priority: high
tags:
    - unverified
created_at: 2026-05-15T19:09:57Z
updated_at: 2026-05-15T19:36:43Z
---

## Problem

Turn-context assembly is duplicated across **5 entry points** with **3 near-duplicate resolvers**, each drifting independently. The CLI bug we just hit (`isaac prompt --session X` ignoring the session's stored `:crew`) is one symptom; the routing inconsistencies between hooks, ACP, cron, and CLI are the same root cause.

Today:

| Entry | Crew-id source | Honors session `:crew`? | Honors session `:model`? |
|---|---|---|---|
| CLI `prompt` | `--crew` flag → `"main"` | ❌ (bug) | ❌ |
| ACP `session/prompt` | session `:crew` → `"main"` | ✅ | ✅ |
| Webhook hook | hook config `:crew` → `"main"` | ❌ | ❌ |
| Cron | job config `:crew` (required) | n/a (new session) | n/a |
| `bridge/dispatch!` | request `:crew-id` → `"main"` | reads cfg snapshot | — |

Three near-duplicate resolvers all produce the same `{:soul :model :provider :context-window …}` bundle:
- `config/resolve-crew-context` (loader.clj:1171) — most complete
- `session-context/resolve-turn-context` (session/context.clj:11) — alt for injected crew
- `acp/resolve-crew-model` (server.clj:63) — duplicates the above inline

`bridge/dispatch!` already calls `resolve-turn-opts` internally, but callers pre-resolve and stuff the bundle into the request, so the inner resolve is a redundant second pass nobody trusts.

## Strategy

Make `bridge/dispatch!` the **single** resolve point. One normalized request shape. One resolver call. Entry points become thin adapters with channel-specific intake only.

### Normalized request to `bridge/dispatch!`

```clojure
{:session-key    "..."              ; required
 :input          "..."               ; required (message text)
 :crew-override  nil | "id"          ; explicit user/channel override
 :model-override nil | "alias"       ; explicit override
 :origin         {:kind :cli|:acp|:webhook|:cron :name "..."}
 :cwd            nil | "path"        ; for new-session creation
 :comm           channel}
```

### Inside `dispatch!`

1. Load existing session from store (may be nil).
2. Resolve crew: `(or crew-override (:crew session) (default-crew cfg) "main")`.
3. Resolve model: `(or model-override (:model session))` — passed as `:model-override` to step 4. Crew default applied inside the resolver when nil.
4. Call `config/resolve-crew-context cfg crew-id {:model-override …}` — the **single** resolver.
5. Validate (unknown crew? unknown model?) → emit error via `comm`.
6. If no session, `store/open-session!` with `:crew :origin :cwd`.
7. Hand context to existing turn machinery.

The `--crew` flag winning over session falls out of step 2.

### Entry-point adapter table

| Adapter | `crew-override` source | `model-override` source |
|---|---|---|
| CLI `prompt` | `--crew` flag | `--model` flag |
| ACP `session/prompt` | nil | request `:model` |
| Webhook hook | `(:crew hook)` | `(:model hook)` |
| Cron | `(:crew job)` | nil |

Hook and cron configs effectively *are* per-fire overrides — same slot, different intake.

## Deletions

- `bridge/prompt_cli/resolve-run-opts`, `run-base-context`, `resolve-provider-instance`
- `comm/acp/server/resolve-crew-model`
- Inline `turn-opts` assembly in `hooks.clj:165-175`
- Inline assembly in `cron/scheduler/fire-job!`
- `session/context/resolve-turn-context` — fold into `resolve-crew-context` by extending it to accept `{:crew-members :models}` overrides for the injected-crew (test) path
- Redundant second-pass `resolve-turn-opts` inside `bridge/dispatch!`

After this: **`config/resolve-crew-context` is the only crew/model/provider resolver in the codebase.**

## Behavior changes worth flagging

1. **CLI `prompt --session X` now respects session crew.** Bug fix; behavior change for anyone who relied on the old "always main" default.
2. **Stale session `:crew`** becomes load-bearing routing data. If a session has a `:crew` no longer in config, the resolver emits "unknown crew on session X — pass `--crew` to override" rather than silently falling back to main. ACP already does this check; lift it to the funnel.
3. **Provider instance is built once per dispatch.** Some paths build it twice today.

## Sequencing

Single bean, single PR, in this order:

- [ ] Extend `config/resolve-crew-context` to absorb the injected-crew path and accept `:model-override` uniformly
- [ ] Rebuild `bridge/dispatch!` to take the normalized request shape and own all resolution
- [ ] Convert CLI `bridge/prompt_cli` adapter to thin shim
- [ ] Convert ACP `comm/acp/server` adapter to thin shim
- [ ] Convert webhook `hooks.clj` adapter to thin shim
- [ ] Convert cron scheduler adapter to thin shim
- [ ] Delete the 4 duplicate resolvers + redundant inner resolve
- [ ] Update spec files (`spec/isaac/bridge/`, `spec/isaac/comm/acp/`, `spec/isaac/hooks_spec.clj`, `spec/isaac/cron/`) — expect more deletions than additions
- [ ] Update Gherkin features under `features/` for prompt/hook/cron flows
- [ ] Verify `bb spec` clean before handoff

## Acceptance

- `isaac prompt --session <pinky-session>` (no `--crew`) routes through pinky's crew default (not main).
- `isaac prompt --session <pinky-session> --crew main` overrides to main.
- All four channels share one resolver call path.
- Only `config/resolve-crew-context` remains as a turn-context resolver in `src/`.
- All specs and features green.
