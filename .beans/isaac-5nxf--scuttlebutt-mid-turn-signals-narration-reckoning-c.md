---
# isaac-5nxf
title: 'Scuttlebutt: mid-turn signals (narration, reckoning, cycle boundaries, tool progress, bulletins) — Comm surface redesign'
status: draft
type: feature
priority: normal
created_at: 2026-08-30T15:54:52Z
updated_at: 2026-08-30T22:05:14Z
---

Design discussion 2026-08-30 (Micah + plan). Recall term: **scuttlebutt** — the
cask the crew talked around; the mid-turn chatter a turn produces beyond its
final reply. Repos: **isaac-agent** (protocol + emission + provider adapters +
transcript), then **isaac-acp**, **isaac-discord**, **isaac-imessage**, CLI.

## What exists today (isaac-agent `comm/protocol.clj`)

`Comm` protocol: on-turn-start, on-text-chunk, on-tool-call/cancel/result,
on-compaction-start/success/failure/disabled, on-turn-end, send!. Seven
impls: null, cli, memory, prompt_cli, api (in tree) + discord, acp, imessage.

- Assistant text between tool calls IS streamed (`turn.clj` `chat-fn-for` →
  `stream-response!` → `on-text-chunk`) — but a comm cannot tell narration
  from the final reply; both arrive on the same hook.
- Tool call/result events exist; Discord and iMessage no-op them. ACP and
  CLI render them (toad status rows, `🧰 name` lines).
- Reasoning/thinking is DROPPED everywhere: responses.clj requests
  `reasoning.summary "auto"` and only `log/debug`s it; claude_cli.clj keeps
  only `"text"` stream events; messages.clj surfaces nothing.
- Discord: typing indicator at turn start, one message at turn end. That is
  a rendering choice, not a model limitation. Models do not need prompting
  to narrate; the pipe already carries it.

## The signals (vocabulary)

| signal | source | proposed name |
|---|---|---|
| assistant text in a cycle that ends in tool calls | model | **narration** |
| final assistant text (cycle with no tool calls) | model | **reply** |
| provider reasoning / thinking summaries | provider | **reckoning** (dead reckoning — the crew's internal computation, not speech) |
| a tool ran / finished / partial output of a running tool | tool runner | tool-call / tool-result / **tool-progress** (any tool; exec is just the one with incremental output today) |
| things the SHIP did: compaction, recall injected, episode opened/closed, turnstile hold, hail deferred | runtime | **bulletin** `{:kind …}` |

**Key insight: the missing primitive is the CYCLE, not a method.** The driver
streams deltas without cycle boundaries, and while streaming it cannot know
whether the cycle will end in tool calls (narration) or not (reply). With
`cycle-start` / `cycle-end {:outcome :tool-calls|:reply :text …}` a comm can
buffer a cycle and classify at the boundary. An explicit `narration` signal
is therefore only possible AFTER cycle end — which is what cycle-end's
`:text` delivers. Decide whether to also emit a convenience `narration`
event at cycle end.

## Surface design — OPEN QUESTION (Micah: "a replacement is better than a patch")

Option A — second opt-in protocol `CycleComm` (cycle-start/end, reckoning,
tool-progress, bulletin) + driver-side `emit!` that no-ops when
`(satisfies? CycleComm ch)` is false. Zero-risk for existing impls; but it
is a patch, and a `defprotocol` method an impl forgets is an
AbstractMethodError at call time, not a compile error.

Option B — REPLACE `Comm`'s turn-event methods with ONE event-shaped
method: `(on-event [comm session-key event])`, `event` =
`{:kind :turn/start | :cycle/start | :cycle/end | :text/chunk | :reckoning |
:tool/call | :tool/cancel | :tool/result | :tool/progress | :bulletin |
:turn/end, :payload …}`. Comms `case` on `:kind`; unknown kinds fall
through; new signals never touch impls. `send!` stays a separate method
(outbound delivery, not a turn event). Migration: every impl in four repos
on one train (or a shim adapting old-protocol impls to on-event during the
transition).

Option C — multimethod `(defmulti on-signal (fn [comm event] [(comm-kind
comm) (:kind event)]))` with a `:default` no-op. Most extensible; loses the
per-impl "did you implement it" signal entirely; comm identity becomes a
keyword rather than a type. Micah leaning here ("maybe multimethods would be
better than a protocol. Hmm…") — decide.

Regardless of option: the four `on-compaction-*` become bulletins.

## Provider adapters (isaac-agent)

- responses.clj: forward reasoning summary deltas from the stream as
  reckoning (already requested; today log-only).
- claude_cli.clj: stop filtering to `"text"`; forward `thinking` events.
- messages.clj: forward `thinking` deltas when extended thinking is on.
- Per-model opt-out (`:reckoning false`) for noisy models.

## Transcript — OPEN QUESTION

Persist reckoning as a new entry type `"reckoning"` (replayable, ACP
session/load, best debugging record we have never had), EXCLUDED from the
prompt builder (never re-sent to the model) and from scene segmentation /
recall. Alternative: stream-only, never persisted. Plan recommends persist.

## Module renderings (one bean each, after the agent bean deploys)

- ACP: reckoning → `agent_thought_chunk`; narration → `agent_message_chunk`
  (toad renders both natively, like Claude Code).
- Discord: post one "working…" message at cycle 1, EDIT it in place with
  narration + `🧰 tool` lines per cycle, replace with the reply at turn end;
  bulletins as one italic line; message-cap aware.
- iMessage: reply-only (no edits); maybe bulletins for compaction/holds.
- CLI: dim narration; `⋯ reckoning` lines behind a verbose flag.

## Sequencing / dependencies

Agent bean first (protocol + emission + adapters + transcript; with no
rendering comm changes the existing suites are the regression net), then
acp + discord in parallel. Independent of isaac-6yg0 (ACP through the
bridge), isaac-ohsy-B, isaac-pqjn — different files — but land after 6yg0
so the ACP module is on current pins.

## Decisions needed before scenarios

1. Surface: A (opt-in protocol) / B (single on-event method, replacement) /
   C (multimethod).
2. Persist reckoning in the transcript, or stream-only.
3. Emit a convenience `narration` event at cycle end, or let comms derive it
   from cycle-end.



## Decision (2026-08-30, Micah + plan) — Option D: protocol + defaults map

Micah's constraints: case statements frowned upon (kills B's on-event); wants
a REPLACEMENT of Comm, not a patch (kills A); comms carry state in
deftype/defrecord fields (weakens C's multimethods).

Resolution: **redesign the Comm protocol freely** (turn/cycle boundaries,
reckoning, tool-progress, bulletin as first-class methods) and make partial
implementation safe with a published defaults map:

- `isaac.comm.protocol/defaults` — a plain fn-map, one no-op per method.
- Comms declare state-only `deftype` and attach the protocol EXTERNALLY:
  `(extend TheType Comm (merge comm/defaults {overrides}))`. Fns receive
  `this`; deftype fields stay publicly readable. New signals later = one
  entry in defaults, zero module edits.
- **Trap to enforce**: inline (deftype-body) protocol implementation
  bypasses defaults — missing methods are AbstractMethodError, and an
  `Object` fallback does not rescue inline implementors. Convention:
  comms never implement Comm inline. Enforce via (a) a `defcomm` macro
  generating deftype + merged extend, and/or (b) a conformance spec that
  calls every protocol method on each comm (catches bare inline impls).
- `send!` may stay on the same protocol; it gets no default (delivery is
  mandatory per comm).
- Perf note: external extend dispatches via the protocol cache, not direct
  interface calls — nanoseconds, irrelevant at per-turn event rates.
- on-compaction-* fold into bulletins as part of the replacement.

Still open before scenarios: (1) persist reckoning in the transcript (plan
recommends yes, excluded from prompt builder + recall); (2) convenience
`narration` event at cycle end vs comms deriving it from cycle-end.



## Confirmed (2026-08-30, Micah)

- **One-time overhaul of every implementor** as part of the replacement —
  no shim, no transition adapter. Inventory: in-tree null, memory, cli,
  prompt_cli, api (isaac-agent) + discord, acp, imessage (module repos).
  Module repos migrate on their own trains after the agent bean lands;
  their pinned agent SHAs gate them, so in-tree and modules need not land
  simultaneously — but each module's FIRST pin bump past the agent bean
  must carry its migration (compile break is loud, not silent, since
  inline impls of removed methods fail at compile/load).
- **`defaults` lives in the same namespace as the protocol**
  (`isaac.comm.protocol`): the protocol, the defaults map, and (if built)
  the `defcomm` macro are one surface.
