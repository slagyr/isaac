---
# isaac-vrtb
title: Remove compaction-disabled; keep compacting and page attention
status: draft
type: bug
priority: high
created_at: 2026-08-31T14:15:35Z
updated_at: 2026-09-04T16:33:53Z
---

Likely repo: **isaac-agent** (session schema, drive compaction, attention). Also **isaac-hail** (fixtures that used the flag to force `:context-exhausted`) and the Comm protocol method `on-compaction-disabled` in **isaac-server** plus no-op impls in **isaac-discord / isaac-acp / isaac-imessage**.

## Problem

After 5 consecutive compaction failures a session gets `:compaction-disabled true`. From then on every turn skips compaction, hits the context guard, and defers `:context-exhausted` forever. Hail retries it on a 5-minute cadence. The session is dead; the only recovery is manual unset. Compaction is necessary — disabling it is the expensive failure mode, not a safety valve.

Attention for this path is incomplete: `maybe-notify-compaction-disabled!` fires once as the flag is set, and the 1h per-session throttle atom in `isaac.attention` is never consulted.

## Decisions (2026-09-04, Micah)

1. **Delete `:compaction-disabled`.** Never skip compaction because earlier attempts failed. Clean cutover: drop the key from the session schema, stop writing it, delete the `session__model` clearer. Unknown keys already drop on read (`schema_spec` "drops unknown keys on read"), so live `session.edn` files that still carry the flag just stop honoring it — no migrator.
2. **Keep compacting.** In-turn `max-compaction-attempts` (recursive compact tries on *this* request) stays. Cross-turn `:compaction.consecutive-failures` stays as a counter (success still resets to 0). It is not a kill switch.
3. **Page, don't brick.** On the 5th consecutive compact failure: post attention (Discord notify coords) and keep the existing `:compaction/failure` bulletin. Do **not** emit `:compaction/disabled`. Wire the unused 1h per-session throttle in `isaac.attention` so a stuck session pages once an hour, not every turn.
4. **This-turn save-or-defer.** `compaction-cannot-save-turn?` is true only when compact failed or exhausted in-turn attempts *on this turn*. p9zy overflow compact-and-retry is unchanged; hail still sees `:context-exhausted` weather when *this* request cannot be saved — not because a flag was set last Tuesday.
5. **Protocol.** Remove `on-compaction-disabled` from the Comm protocol and every impl. Agent `on-bulletin` `:kind :compaction/disabled` goes away with it.

## Behavior

- A session at 5 consecutive compact failures still runs compaction on the next turn that needs it.
- The 5th failure (and then at most once per hour while still failing) enqueues attention: session key, consecutive-failures, tokens, window. Copy is "Compaction failing for session …", never "disabled".
- A later successful compact resets `consecutive-failures` to 0 (same as today).
- Existing disabled sessions on disk (`orchestration-work` on zanebot) start compacting again on the next turn after deploy.

## Existing scenarios to delete or rewrite (isaac-agent)

| file | verdict |
|------|---------|
| `features/session/context_window_guard.feature` "compaction disabled over the guard line defers without an LLM request" | **delete** — that *is* the brick |
| `features/session/context_window_guard.feature` "compaction failure cap posts attention…" | **rewrite** — attention still posts; session has no disabled flag; next needing turn still compacts |
| `features/session/compaction_logging.feature` "compaction stops retrying after max-compaction-attempts consecutive cross-turn failures" | **rewrite** — still logs failure; no `:compaction-disabled`; no `:compaction/disabled` bulletin |
| `features/session/compaction_logging.feature` "switching model clears compaction-disabled…" | **delete** — nothing to clear |
| `features/session/compaction_overflow.feature` "prompt-too-long with compaction disabled is context-exhausted weather" | **rewrite** — overflow after *this-turn* compact cannot save still defers (p9zy); do not seed a disabled flag |

Hail `features/context_window_guard.feature` seeds `compaction-disabled true` to force exhausted weather. Rewrite those Givens (queued `:unavailable` / this-turn compact fail + over-guard last-input). Do not keep a test-only flag.

## Out of scope

- Changing the 0.8 compact threshold or p9zy overflow retry.
- Replacing in-turn `max-compaction-attempts`.
- A runbook for the old opus-window recovery trick.

## Acceptance

Draft until `@wip` scenarios exist. Then:

```
cd isaac-agent
bb features features/session/context_window_guard.feature
bb features features/session/compaction_logging.feature
bb features features/session/compaction_overflow.feature
bb spec spec/isaac/drive/turn_spec.clj spec/isaac/attention_spec.clj spec/isaac/session/schema_spec.clj spec/isaac/tool/session_spec.clj
```

Plus hail fixture rewrite green. No remaining `compaction-disabled` in agent schema, drive, or session tool. No `@wip` on the rewritten rows.
