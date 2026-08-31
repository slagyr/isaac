---
# isaac-frvu
title: 'Scuttlebutt renderings: per-comm contract — how every Comm impl handles the new surface'
status: completed
type: task
priority: normal
created_at: 2026-08-30T23:29:05Z
updated_at: 2026-08-31T16:05:40Z
blocking:
    - isaac-i5ps
    - isaac-pq0b
---

Planning bean (2026-08-30, Micah). Output: a decision table, one row per Comm
implementation, settling how each renders the new surface (chatter /
reckoning / aside / reply / tool-call / tool-cancel / tool-result /
tool-progress / bulletin / turn-start / turn-end) — BEFORE the module
implementation beans (isaac-i5ps ACP, isaac-pq0b Discord) get scenarios, and
feeding the per-comm contract section into isaac-5nxf for the in-tree impls.

## Why

isaac-5nxf plans the interface; the implementations were only sketched. The
sketches currently living in i5ps/pq0b/5nxf are PROPOSALS, not decisions
(Micah 2026-08-30). Each comm must pick ONE altitude per voice — streams
(chatter/reckoning) or verdicts (aside/reply) — or it double-renders; that
choice, plus what to do with bulletins and tool events, is a per-surface UX
decision Micah should make deliberately.

## The table to fill (current sketch as starting point)

| comm | altitude | reckoning | tools | bulletins | notes |
|---|---|---|---|---|---|
| null | defaults everywhere | drop | drop | drop | trivially decided |
| memory | records EVERYTHING | record | record | record | reference impl; dictated by scuttlebutt.feature |
| cli (comm/cli.clj) | chatter (today's stream, byte-identical) | ⋯ dim lines behind a verbose flag? | 🧰/← lines as today | print? | UX decision |
| prompt_cli | same as cli? they differ today — inventory first | ? | ? | ? | |
| api (in-tree) | ? — inventory what it forwards today | ? | ? | ? | |
| ACP (i5ps) | streams: chatter → agent_message_chunk, reckoning → agent_thought_chunk | native | native notifications as today | session/update status? which kinds? | closest to protocol mapping, least contentious |
| Discord (pq0b) | verdicts; live-turn option: edit-in-place working message w/ latest aside + tool lines | drop? or into the working message? | into working message when live-turn on | one italic line? which kinds? | flag per channel vs default-on; rate limits |
| iMessage | reply-only? | drop? | drop? | compaction/hold notices? | pure assumption today — decide |

## Decisions the session must produce

1. Per-comm altitude + per-signal row for every impl (table above completed).
2. **No-double-render rule made testable**: conformance spec asserts a comm
   does not implement both on-chatter and on-aside/on-reply as renderers
   (or defines the sanctioned exception, e.g. memory).
3. Which bulletin kinds are user-facing per surface (compaction? recall?
   turnstile holds? episode rotation is supposed to be INVISIBLE —
   51xy decision 27 — so :episodes/* likely renders NOWHERE except memory).
4. Reckoning visibility defaults: on for ACP/CLI-verbose, off elsewhere?
   Per-crew or per-comm config knob, if any.
5. In-tree contract lands as an amendment to isaac-5nxf acceptance
   (byte-identical UX for cli/prompt_cli/api through the migration);
   module rows become the scenario seeds for i5ps / pq0b / a new imessage
   bean if its row is non-trivial.

## Inputs to gather at session start

- Inventory of what cli / prompt_cli / api actually render today (they are
  not identical; read before deciding).
- Discord rate-limit budget for edit-in-place (edits per turn at
  tool-loop-max 400 is not viable unthrottled).
- ACP protocol: which session/update kinds toad renders.

Draft until the planning session runs. Blocks isaac-i5ps and isaac-pq0b
(already linked); informs isaac-5nxf but does not block it — the in-tree
contract there is "byte-identical, one altitude", already its regression net.



## Session 1 results (2026-08-31, Micah + plan)

Inventory attached (read of all impls):
- cli (comm/cli.clj): prints text chunks; '[tool call: name]' lines; newline
  at turn end; everything else no-op. Plain.
- prompt_cli: very different from cli — accumulates text (prints when live),
  🧰 tool-call + ← tool-result on stderr, ALL FOUR compaction states on
  stderr (🥬/✨/🥀/🪦). The rich one.
- api.clj: re-exports the protocol only — NOT an implementor; drop it from
  the table (migration touches null, memory, cli, prompt_cli + 3 modules).
- ACP: has an UNUSED agent_thought_chunk builder — reckoning pipe ready.
  tool_call/tool_call_update lifecycle documented (pending→completed).
- Discord: typing at start + one message at end. iMessage: no-ops every
  turn event; replies ride send! delivery only.
- Discord rate-limit note: message edits share the per-channel ~5 req/5s
  bucket — live-turn editing must throttle (≥2s/edit, coalesce) or 429s.

DECIDED (Micah): **null and memory rows as proposed** — null = defaults +
send! stub; memory = records everything (dictated by scuttlebutt.feature).

REMAINING: one REVIEW SESSION PER COMM, in Micah's order: cli, prompt_cli,
ACP, Discord, iMessage. The recommendation rows in the table above are
seeds, not decisions. The cross-cutting questions (reckoning default
per-comm vs per-crew knob; bulletin visibility incl. :episodes/* invisible
and holds-via-attention; Discord phase split + opt-in flag; iMessage
break-glass ping) are to be settled inside the relevant comm's session,
not globally.



## CLI review session (2026-08-31, Micah) — DECIDED

Findings that reframed it: CliComm's only users are the two fallback sites
in drive/turn.clj (:1084, :1268) — every comm-less charge (hail deliveries,
i.e. WORKERS) renders through it into server stdout; and isaac-server ships
a divergent older copy of the same namespace (classpath-order roulette).

Rulings (Micah):
1. **CliComm is the wrong fallback.** Comm-less turns get a new **LogComm**:
   renders every signal through isaac.logger as structured events (worker
   chatter/asides/tool events land in server.log where they belong), not
   stdout prints. null would lose the signal; LogComm keeps it observable.
2. **Kill the divergence**: delete isaac-server's src/isaac/comm/cli.clj.
   And with the fallback gone, agent's CliComm has zero users — DELETE BOTH.
   There is no 'cli comm'; the interactive CLI surface is prompt_cli.
3. **Principle (applies to all CLI-ish comms, decided here, implemented in
   the prompt_cli session): the official model response must be
   identifiable. stdout carries ONLY the reply; ALL other activity (chatter,
   asides, reckoning, tool events, bulletins) goes to stderr**, with CLI
   options to control output levels (e.g. quiet / normal / verbose tiers —
   exact flags decided in the prompt_cli session). This supersedes
   byte-identical for the interactive CLI where reply-vs-activity was
   previously mixed on stdout.

Scope consequences → isaac-5nxf (amended there): LogComm (new, in-tree,
conformance-covered) replaces the fallback; both CliComm copies deleted;
in-tree implementor list becomes null, memory, LogComm, prompt_cli.

Next review session: **prompt_cli** (stdout/stderr split + output-level
flags + reckoning flag + bulletin mapping).



## CLI review — CORRECTED AND FINAL (2026-08-31, Micah)

Supersedes the LogComm ruling above (that was an idea recorded prematurely):
1. **Fallback = null comm.** LogComm REJECTED — redundant: the drive already
   logs every event (request-built, chat/*, tool/start, compaction/*), and
   verbatim text lives in the transcript, the source of truth. Future idea
   noted, not planned: `isaac sessions tail <key>` reading the transcript
   (incl. reckoning entries) for live worker-watching.
2. **CliComm dies, both copies** (agent + isaac-server's divergent shadow) —
   zero clients once the fallback is null. `prompt` is unaffected: it uses
   its own PromptComm and never hits the fallback; the fallback only serves
   comm-less charges (hail, cron).
3. **stdout/stderr principle settled, incl. the streaming question**:
   stdout = the reply only (composable: `isaac prompt … | pbcopy` yields the
   answer). stderr = ALL live activity — including chatter, which streams
   there token-by-token, dead ends and all; when the verdict is :reply the
   full reply prints to stdout, and duplication between the stderr stream
   and stdout reply is ACCEPTED (Micah: chatter doesn't always resolve and
   can contain dead ends; stderr is where that belongs).
   Output-level flags (quiet/default/verbose tiers, where reckoning and
   bulletins sit) → the prompt_cli review session.

In-tree implementor list: **null, memory, prompt_cli** (+ null doubling as
the fallback).



## prompt_cli review session (2026-08-31, Micah) — DECIDED

Principle: stdout is the answer, stderr is the theater, tiers only change
how much theater. Errors: stderr always, every tier, plus exit code.

| | --quiet | default | --verbose |
|---|---|---|---|
| stdout | reply | reply | reply |
| chatter (live, dead ends incl.) | — | stderr | stderr |
| 🧰 tool-call / ← tool-result | — | stderr | stderr |
| compaction bulletins 🥬✨🥀🪦 | — | stderr | stderr |
| reckoning (⋯ dim) | — | — | stderr |
| tool-progress | — | — | stderr |
| other bulletins (recall, holds, …) | — | — | stderr |

- Chatter in DEFAULT (today's live feel, relocated to stderr); compaction in
  default (how you tell compacting from hung); reckoning verbose-only
  (inward voice; transcript has it regardless).
- Asides render at NO tier — chatter already streamed those words.
- The old live?/streaming knob DIES — streaming is free on stderr; one flag
  family (-q / -v).
- **No-double-render rule gains a channel qualifier** (→ isaac-5nxf): never
  render the same voice twice TO THE SAME DESTINATION. prompt_cli
  (chatter→stderr + reply→stdout) and memory both pass; chatter+reply both
  to stdout still fails conformance.

Implementation is its own bean (redesign, not the 5nxf mechanical
migration). Scenario seeds: `prompt -m … 2>/dev/null` emits exactly the
reply; -q silences stderr entirely (errors excepted); -v shows ⋯ reckoning
lines; existing 🧰/compaction stderr scenarios keep passing.

Remaining review sessions: ACP, Discord, iMessage.



## ACP review session (2026-08-31, Micah) — DECIDED: option B

agent_message_chunk is ACP's stdout; agent_thought_chunk is its stderr —
the CLI principle applied to ACP's channel pair.

| signal | mapping |
|---|---|
| chatter | agent_thought_chunk, streamed live, 💬-prefixed |
| reckoning | agent_thought_chunk, **🧠-prefixed** (disambiguates the two voices inside the thought channel) |
| reply | agent_message_chunk, whole, at the verdict — the ONLY message content. Duplication with the thought stream accepted (CLI precedent) |
| aside | nothing separate (chatter streamed it) |
| tool-call/result | tool_call pending → tool_call_update completed (unchanged) |
| tool-progress | tool_call_update status in_progress + content append (protocol-native) |
| compaction bulletins | thought chunks as today, adopt the shared emoji vocabulary 🥬✨🥀🪦 |
| recall / episodes / holds bulletins | silent |
| turn end | stopReason unchanged; send! stays stub |

Notes: ACP protocol had a native home for every signal (plan +
current_mode_update remain unused — 'plan' flagged as a future foreman fit).
Clients that hide thought chunks see silence until the verdict; toad shows
them, strictly better than today's everything-is-the-message.
Emoji vocabulary is now CROSS-COMM: 💬 chatter, 🧠 reckoning, 🥬✨🥀🪦
compaction, 🧰/← tools — prompt_cli's -v reckoning lines use 🧠 too
(supersedes the ⋯ placeholder in its table).

Remaining review sessions: Discord, iMessage.



## Discord review session (2026-08-31, Micah) — DECIDED

Micah's driving gripe: the typing indicator dies after ~10s and long turns
go opaque — ANY liveness signal is the big win. Three phases:

**Phase 0 — typing heartbeat (own bean; needs NO scuttlebutt):**
re-send post-typing every ~8s from on-turn-start until on-turn-end, using
today's protocol. Ships independently, immediately.

**Phase 1 — mechanical migration (rides the module's pin bump past 5nxf):**
on-reply posts the reply as a new message (replacing on-turn-end's content
path); on-turn-end renders errors only; typing heartbeat continues; all else
defaults. Zero UX change beyond phase 0.

**Phase 2 — live-turn working message (isaac-pq0b, per-channel opt-in
:live-turn):**
- working message posted at first cycle; EDITED (coalesced ≥2s, honor
  Retry-After, hard cap ~15 edits/turn then freeze) with: latest 💬 aside,
  last ~5 🧰/← tool lines (older collapse to a count), 🥬 compaction line.
- chatter/tool-progress: not rendered (edits are snapshots; asides are the
  right granularity).
- **reckoning: never on Discord, no flag** — shared channels.
- other bulletins silent (holds/deferrals are attention's job).
- verdict: working message collapses to a one-line summary
  (✔ n cycles · 🧰 n tools · Ns) and the reply posts as a NEW message so
  the notification ping fires.
- rest.clj gains bucket-awareness (today only 429→transient) — in pq0b.
- Future note, not planned: Discord THREADS as a full-theater container.

Rate-limit reality recorded: no documented fixed numbers — per-route buckets
via X-RateLimit headers; global 50/s; sends and edits empirically ~5/5s per
channel in separate buckets; hard limit 10k invalid requests/10min = 1h
Cloudflare ban, so honoring Retry-After is the non-negotiable part.

Remaining review session: iMessage.



## iMessage review session (2026-08-31, Micah) — DECIDED

iMessage = pure delivery. All turn events take comm/defaults; send! is the
entire implementation (replies already ride the outbound delivery queue, not
turn callbacks — today's on-turn-end is nil and stays that way via
defaults). Break-glass alerts (compaction-disabled, stuck holds) are the
ATTENTION system's job — it already routes break-glass to imessage; wiring
lands in isaac-vrtb, never in the comm. No typing-bubble phase 0: the imsg
CLI cannot send typing indicators.

## SERIES COMPLETE — final per-comm contract index

- null: defaults + send! stub (also the comm-less fallback; CliComm deleted,
  both copies).
- memory: records everything — reference implementor (scuttlebutt.feature).
- prompt_cli: mechanical in 5nxf; stdout=reply/stderr=theater redesign with
  -q/-v tiers in isaac-7rso.
- ACP: option B — thought channel as stderr (💬 chatter, 🧠 reckoning),
  message = reply at verdict; tool-progress via tool_call_update — isaac-i5ps.
- Discord: phase 0 typing heartbeat isaac-qomx (no scuttlebutt dep);
  phase 1 mechanical on pin bump; phase 2 live-turn working message
  isaac-pq0b.
- iMessage: pure delivery, nothing to do beyond the mechanical defaults
  migration on its pin bump.
Cross-comm emoji vocabulary: 💬 chatter · 🧠 reckoning · 🧰/← tools ·
🥬✨🥀🧟 compaction (🪦 = disabled). Conformance: no same-DESTINATION double
render (per-destination rule in isaac-5nxf).
