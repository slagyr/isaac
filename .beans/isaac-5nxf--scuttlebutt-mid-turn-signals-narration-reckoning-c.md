---
# isaac-5nxf
title: 'Scuttlebutt core: Comm replacement (chatter/reckoning/aside/reply/bulletins, cycles, defaults map) in isaac-agent'
status: todo
type: feature
priority: normal
created_at: 2026-08-30T15:54:52Z
updated_at: 2026-08-31T13:36:52Z
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



## Naming + persistence (2026-08-30, Micah + plan)

- **reckoning** = the abstraction over provider `reasoning` (Responses API:
  `reasoning` output items / `response.reasoning_summary_text.delta`) and
  `thinking` (Anthropic blocks, claude-cli stream-json events). Confirmed.
- **"speech" rejected** (rings of STT/TTS). Reframed from theater/deck, not
  audio. Recommended slate — final pick pending Micah:
  - `on-chatter` — live outward-voice deltas, classification unknown while
    streaming (alternates: on-prose, on-patter, on-utterance, on-text)
  - `on-aside` — cycle-end text when tool calls followed; a theater aside,
    said while doing, not the answer (alternates: on-callout, on-preamble)
  - `on-reply` — cycle-end text with no tool calls
  - `on-narration` is DROPPED from the protocol (was ambiguous with the
    stream reading).
  Full method set otherwise as previously recorded: on-turn-start,
  on-cycle-start/end, on-chatter, on-reckoning, on-tool-call/cancel/result,
  on-tool-progress, on-aside, on-reply, on-bulletin, on-turn-end, send!.
- **DECIDED: reckoning IS persisted** — new transcript entry type
  `"reckoning"`; replayable (ACP session/load, sessions show), EXCLUDED
  from the prompt builder and from recall/scene segmentation.
- Industry note: claude-code persists thinking blocks in session jsonl
  (wire-MANDATED: extended thinking + tool use requires returning
  signature-carrying blocks next request); codex/grok responses agents
  persist reasoning items (encrypted when store:false) for stateless
  resume. isaac has no wire pressure today — 7l5m stateful chaining holds
  reasoning server-side within a turn; Anthropic extended thinking is not
  enabled. **Landmine note:** if extended thinking is ever enabled on
  messages.clj, the TOOL LOOP must carry thinking blocks within the turn
  regardless of transcript policy.

All design decisions closed except the chatter/aside name pick. Next:
split into beans (agent core; acp rendering; discord rendering) and write
@wip features.


## FINAL DESIGN (2026-08-30, Micah approved names)

Names locked: **chatter / reckoning / aside / reply / bulletin.**

- **reckoning** — inward voice; the model working out what to do (Responses
  `reasoning` summaries, Anthropic `thinking`). Not addressed to the user.
  Dead reckoning at the chart table.
- **aside** — outward voice, not the answer: what the model says on its way
  into tool calls ("Let me pull up that scene."). Theater aside.
- **reply** — outward voice, the answer; the cycle with no tool calls.
- **chatter** — the SAME outward words as aside/reply, live, before the
  verdict. At cycle end the buffered chatter is re-delivered whole as
  on-aside or on-reply. A comm picks its altitude: streams (on-chatter) or
  verdicts (on-aside/on-reply), never both.
- Discriminators: addressed-to-you? × is-the-answer?
  reckoning no/no · aside yes/no · reply yes/yes · chatter = yes/undetermined.

### Protocol (isaac.comm.protocol — replaces Comm outright)

```clojure
(defprotocol Comm
  ;; turn envelope
  (on-turn-start   [comm session-key input])
  (on-turn-end     [comm session-key result])   ; once, every outcome
  ;; cycle boundaries (one LLM call each)
  (on-cycle-start  [comm session-key cycle])                 ; {:n 3 :model "..."}
  (on-cycle-end    [comm session-key cycle outcome])        ; {:outcome :aside|:reply
                                                            ;  :text ".." :tool-calls [..]}
  ;; live streams
  (on-chatter      [comm session-key cycle chunk])  ; was on-text-chunk
  (on-reckoning    [comm session-key cycle chunk])
  ;; classified text at the boundary
  (on-aside        [comm session-key cycle text])
  (on-reply        [comm session-key text])
  ;; tools
  (on-tool-call    [comm session-key tool-call])
  (on-tool-cancel  [comm session-key tool-call])
  (on-tool-result  [comm session-key tool-call result])
  (on-tool-progress[comm session-key tool-call chunk])
  ;; the ship (replaces the four on-compaction-*)
  (on-bulletin     [comm session-key bulletin])     ; {:kind :compaction/start
                                                    ;  | :recall/injected | :episodes/opened
                                                    ;  | :turnstile/held ... :payload {..}}
  ;; outbound delivery — unchanged, NO default
  (send!           [comm record]))

(def defaults "no-op fn per turn-event method; :send! deliberately absent" {...})
```

Implementor convention (one-time overhaul, all 8 impls): state-only
deftype + `(extend TheType Comm (merge comm/defaults {overrides}))` — never
inline. Discord's whole migration ≈ on-turn-start (typing), on-reply (send),
on-turn-end (errors), send!. Conformance spec calls every method on every
comm to catch inline strays; optional `defcomm` macro later.

### Turn timeline a comm experiences

turn-start → bulletin(:recall/injected) → [cycle-start → reckoning* →
chatter* → tool-call* → cycle-end{:aside} → aside → tool-result*]… →
[cycle-start → chatter* → cycle-end{:reply} → reply] → turn-end.

Redundancy is deliberate: dumb comms take verdicts, ACP takes streams
(chatter → agent_message_chunk, reckoning → agent_thought_chunk), defaults
make both free.



## Scenarios (committed @wip at slagyr/isaac-agent 3cf66a8) — this bean = AGENT CORE

features/comm/scuttlebutt.feature — 4 scenarios:
- :22 chatter resolves into an aside (tools follow) / the reply (none) — full
  timeline: cycle numbering, boundary classification, event order.
- :46 reckoning: streamed to the comm, persisted as a "reckoning" transcript
  entry, ABSENT from the next LLM request.
- :68 compaction reaches the comm as bulletins (:compaction/start,
  :compaction/success).
- :92 a streaming mock tool ("test__sounding") emits tool-progress through
  the handler ctx :progress! seam.

Migrated under @wip (failing rows ARE the migration spec):
- features/comm/memory.feature:12 text-chunk → chatter + reply.
- features/session/llm_interaction.feature:93 text-chunk → chatter.
- features/session/compaction_logging.feature:20/:91/(huge-head) compaction-*
  events → bulletin rows with kind column.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / the following sessions exist / the built-in tools are registered / the following model responses are queued / the user sends … via memory comm / the memory comm has events matching / session has transcript(: and matching) / the isaac EDN file … exists with | reuse |
| **queued response row `type: reasoning`** | **NEW fixture — grover emits it as a reckoning delta** |
| **queued row with text + tool_call in ONE response** | **VERIFY the grover queue supports it (row-per-chunk semantics suggest yes); if not, extend the fixture in this bean** |
| **the LLM request does not contain {string}** | **NEW — absence assert on the last outbound request** |
| **a streaming tool {name} is registered that emits progress ["…"] and returns {string}** | **NEW — registers a mock tool whose handler calls the ctx :progress! seam** |
| memory comm event columns `cycle`, `outcome`, `kind` | **reuse-with-revision — MemoryComm (rewritten as the reference extend+defaults implementor) records them** |

## Production shape (agent core)

- isaac.comm.protocol: replace Comm per FINAL DESIGN; `defaults` map in the
  same ns; send! has no default.
- All in-tree impls overhauled to extend+merge: null, memory (reference),
  cli, prompt_cli, api. Conformance spec invokes every method on every comm.
- drive/turn.clj + tool_loop: cycle-start/end emission wrapping each chat
  call; chatter (was on-text-chunk); aside/reply at the boundary; bulletins
  from the compaction paths (legacy on-compaction-* methods DELETED).
- Tool registry: handler ctx gains :progress! → on-tool-progress. Exec
  ADOPTION is the follow-up bean, not here.
- responses.clj reasoning-summary deltas + claude_cli thinking events →
  on-reckoning; grover `reasoning` fixture row. messages.clj thinking is a
  follow-up (extended thinking not enabled).
- Transcript: "reckoning" entry type; excluded from prompt builder + recall
  segmentation.

## Acceptance

Remove @wip from features/comm/scuttlebutt.feature and from the four
migrated scenarios, then:

    bb features features/comm/scuttlebutt.feature features/comm/memory.feature
    bb features features/session/llm_interaction.feature features/session/compaction_logging.feature
    bb spec spec/isaac/comm spec/isaac/drive

Full bb features 0 failures under budget — EXCEPT any scenario covered by
the pre-existing red noted below.

**Known pre-existing red (NOT this bean):** features/comm/memory.feature
"Compaction triggers during a memory comm turn" fails on clean main
(transcript lacks the compaction entry) — being scoped; see the
suite-health bean filed alongside.

## Module follow-ups (created as siblings)

acp rendering, discord rendering, exec tool-progress adoption, terminology
renames — see blocking/blocked-by links.



## In-tree per-comm contract (2026-08-31, plan — the slice of isaac-frvu this bean needs)

Byte-identical UX through the migration; each in-tree comm takes ONE
altitude and defaults for the rest:
- **null** — comm/defaults for everything (plus send!).
- **memory** — records every event incl. cycle/outcome/kind columns; the
  reference implementor, dictated by scuttlebutt.feature.
- **cli / prompt_cli / api** — on-chatter renders exactly what on-text-chunk
  rendered (byte-identical stdout); tool call/result lines unchanged;
  aside/reply/reckoning/cycle/bulletin all defaults. Inventory each before
  migrating — they are not identical today; preserve each one's current
  output, do not unify them in this bean.
- **Conformance spec addition**: for each comm, at most one of
  {on-chatter} / {on-aside, on-reply} is non-default (memory exempt) — the
  no-double-render rule, made testable.
Module comms (acp/discord/imessage) are NOT this bean: isaac-frvu plans
their rendering; they migrate in their own repos afterward.

## Dispatch coordination (2026-08-31)

isaac-x2up is in flight in drive/turn.clj + compaction. Sequence your landing:
implement freely on the bean branch, but REBASE onto main after x2up merges
and rerun the full gate before tagging unverified. If x2up's fix moves the
compaction emission points this bean touches, prefer x2up's structure and
adapt. Full-suite acceptance inherits x2up's outcome: 0 failures expected
once it lands; if x2up is still open when you finish, the 18 known
compaction reds are excluded and everything else must be green.
