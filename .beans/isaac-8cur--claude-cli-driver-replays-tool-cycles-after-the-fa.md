---
# isaac-8cur
title: 'claude-cli driver replays tool cycles after the fact: N identical stamps and N full transcript reads per turn'
status: todo
type: bug
priority: normal
tags:
    - accounting
    - claude-code
created_at: 2026-09-20T20:32:30Z
updated_at: 2026-09-20T20:32:52Z
blocking:
    - isaac-dgod
---

The claude-code loop driver fires one `on-cycle :end` per tool call *after* the
turn has already completed, replaying cycles that never happened as separate
events — each carrying the same response object.

`isaac-claude-code/src/isaac/llm/api/claude_cli.clj:1144`:

```clojure
(when-not @live-tool-cycles?*
  (doseq [[i tc] (map-indexed vector tool-calls)]
    (fire-on-cycle! on-cycle :end @cycle-n*
                    (cycle-response response [tc] (get asides i)))
    (fire-start!)))
```

`response` is loop-invariant. Only `tc` and the aside differ. There is no
`chat-fn` call inside the loop, so all N fires land in one synchronous burst.

## Observed 2026-09-20 on zanebot (isaac-verify, claude-opus-5)

- 27 × `:session/stamp-implausible :prompt-tokens 802832 :context-window 200000`
- every value identical; all 27 within **153 ms** (20:15:39.653815Z -> 20:15:39.806953Z)
- transcript at the time: 68 entries, 144,628 bytes

27 tool calls produced 27 fires. Each `on-cycle :end` reaches
`stamp-provider-prompt!` (isaac-agent `turn.clj:1428`), which logs the warn and
then calls `last-transcript-id` -> `policy/get-transcript` — **a full transcript
read from disk, per fire**, to obtain one id.

## Why it matters

- O(cycles x transcript) disk reads per turn. At 68 entries / 144 KB it is ~0.9 ms
  a read and merely wasteful. On the 2.5 MB sessions seen earlier the same day,
  with a 100-cycle budget, it is not.
- It floods the log at WARN, burying real signal.
- It repeats a stamp that should be written once per turn, so any defect in the
  stamped value is multiplied (see isaac-dgod).

## Questions

1. When is `live-tool-cycles?*` false in practice? If the `:before` path fires
   for live cycles, the replay is a fallback for a case that may no longer occur.
2. Should the replay exist at all? Its apparent purpose is to give `comm` a
   per-tool-call event stream. If so, that is a comm concern and should not be
   routed through `on-cycle`, which also drives token stamping.
3. The `:before` path fires with `:usage (zero-usage)`. That is currently
   harmless only because `stamp-provider-prompt!` guards with
   `(when (pos? prompt-tokens) ...)`. Worth a scenario so it stays harmless —
   compare isaac-166j, where zeros did reach the gauge.

## Acceptance

- a claude-code turn with N tool calls fires at most one token stamp, and reads
  the transcript at most once for stamping purposes
- per-tool-call comm events, if still wanted, are delivered without invoking the
  token-stamping path
- `:session/stamp-implausible` does not appear in a normal claude-code turn
- a turn whose cycles arrive live and a turn whose cycles are replayed produce
  the same number of stamps

## Relationship

Sibling of isaac-dgod, which owns the *value* being stamped (a turn total read
as a current prompt size, on both claude-code and chatgpt-stateful). This bean
owns the *frequency*. Split because they live in different modules with
different baselines: this one is isaac-claude-code, dgod is isaac-agent.
