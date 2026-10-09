---
# isaac-1hfe
title: 'Epic: log the wire, share the facts, route the inbound'
status: draft
type: epic
priority: normal
tags:
    - design
    - comm
    - imessage
created_at: 2026-10-02T14:19:22Z
updated_at: 2026-10-02T15:28:05Z
---

# Status

Draft for review. Not actionable. No scenarios yet. Split into child features only after this design is accepted or rewritten.

Motivating incident (2026-10-01): Red Alert pinged Micah over iMessage about Leo's birthday gift. Micah replied in the same Apple thread. The reply landed on zane, who had no Red Alert context and patched `heartbeat-state.json` by hand. That is a workaround, not routing.

Conversation: zane + Micah, 2026-10-01 → 2026-10-02.

# Decisions

Decision (2026-10-02, Micah): Do not build the ledger or the blackboard inside isaac-agent. Agent exposes a seam — listeners for inbound and outbound comm traffic. The ledger is a **module** that implements that seam (storage, retention, query, `comm_recent` are the module's business). The blackboard is a **separate module** that only exposes tools. Agent stays a port; implementations stay swappable.

# Problem

Three problems got tangled. They look related because they showed up in one iMessage thread. They are not one feature.

**1. Routing — who hears Micah's next text.**
Apple gives Isaac one handle and one chat. Discord already multiplexes: the channel *is* the session, each channel binds a crew. iMessage cannot. Inbound today is one session per `chat_guid` (`imessage:<guid>`), crew from that session / defaults. `comm_send` is fire-and-forget. The crew that sent the ping does not hear the reply.

**2. The wire — what was actually said, and which crew said it.**
There is no Isaac-side log of outbound+inbound with a crew column. Apple's `chat.db` is the system-wide transcript, but every outbound looks like "me" — Red Alert and zane are the same handle. isaac-imessage drops `:is_from_me`, so the birthday ping is invisible to the crew that receives the reply. Session transcripts are per-conversation, per-crew — they are not a wire log.

**3. Facts — current world, not conversation.**
"Leo's gift is bought" is a fact Red Alert needs on the next heartbeat. Reconstructing it from a week of texts means every heartbeat LLM-interprets the log. That's how `heartbeat-state.json` got invented (and why it is a log pretending to be state: thousands of `"heartbeat:2026-03-22-1739": "checked"` keys). `memory__*` is crew-scoped prose. Hail is work dispatch. Neither is a named document other crews can mutate.

# What not to do

- **No receptionist / switchboard crew.** Every inbound would hop through a dispatcher model — extra latency, extra cost, wrong personality, single point of failure. Discord would then have two architectures. Keep comms as a port. Crews send. The bus routes.
- **No global transcript injected into session context.** Session context is one conversation with one crew. Dumping every iMessage into every turn is noise (Red Alert's 15-minute heartbeat does not need tax chat) and still does not say who should *act*. Tools that *query* a log: yes. Auto-context: no.
- **Do not couple the three.** A ledger does not decide who hears the reply. A blackboard is not a transcript. Routing policy must work even if nobody queries the log.
- **Do not put storage in the agent.** A JSONL log and a state file are one implementation. The next one might be sqlite, or filtered, or off. Baking them into isaac-agent makes the first implementation the only one.

# Proposed solution

One line: **log the wire, share the facts, route the inbound.**

Agent owns two fire points and a listener berth. Two modules consume that world. Routing stays on isaac-imessage.

## 1. The wire — agent seam + ledger module

Queryable log of what crossed a comm, with the column Apple does not have: **which crew**.

```clojure
{:at      "2026-10-01T14:47Z"
 :comm    :imessage
 :dir     :out
 :crew    :red-alert
 :session "heartbeat"
 :target  "hieronymus@marigold.example"
 :text    "Leo's birthday is in 3 days — might want to grab something!"}
```

### Agent: fire, don't store

New berth `:isaac.agent/comm-listener` (name open — see questions). Map of factories, same shape as `:isaac.agent/tools`. Each contribution is a `CommListener`:

```clojure
(defprotocol CommListener
  (on-outbound [listener record])  ; after successful Comm/send!
  (on-inbound  [listener event]))  ; when the bridge accepts a comm charge
```

**Do not overload `isaac.comm.protocol/Comm`.** Those callbacks are per-surface turn events (typing, chunks, tool-calls) for the comm that owns the session. This seam is bus-level: any comm, with crew, after the fact. Wrong axis.

Fire points (where crew is known):

- **Out** — delivery worker, on successful `Comm/send!`. Today the queue record has `:comm :target :content :id :attempts :created-at` and **no crew/session**. Stamp those at `enqueue!` (from `comm_send` / cron origin). That stamp is **payload for listeners**, not a ledger.
- **In** — bridge, when it accepts a comm charge. First moment inbound has a crew.

Fire semantics: iterate listeners, swallow exceptions, log warn. A listener must not fail a send. Sync is fine; a module that needs network does its own queue. No listeners installed → no-op.

`isaac-imessage` / `isaac-discord` stay pipes. They do not know crew. The bridge never sees `comm_send`.

### Module: store, retain, query

Working name **isaac-ledger**. Contributes:

- one `:isaac.agent/comm-listener` (the writer)
- `:isaac.agent/tools` `comm_recent` (the reader)
- its own `:isaac.config/schema` if it wants retain/truncate settings

Storage, retention, truncation, which comms it persists, heartbeat filtering — all the module. JSONL under `<root>/comm/ledger/` is a first implementation, not the contract. That's the flexibility. Absent from the classpath, nothing is logged.

**How crews read it:** `comm_recent` (last N, filterable by comm / target / dir / crew). On demand. Not in the system prompt.

## 2. Facts — a blackboard module, tools only

Named documents other crews can mutate. Linda tuplespace, not a database.

```
<isaac-root>/state/red-alert/alerts.edn
```

Red Alert owns it (convention, not a lock). Conversational crew writes `{:birthday :Leo :year 2026 :status :done}` when Micah says the gift is bought. Next heartbeat reads it and stays quiet.

Working name **isaac-blackboard**. **No listener. No agent seam.** Contributes `:isaac.agent/tools` `state_get` / `state_put` only — same pattern as hail's `hail-send` / `hail-get`. Whole-document put, last-write-wins, callers do their own read-modify-write. No merge, no etag, no lock — enough for an alerts list, not for accounting.

Storage path and value shape are the module's. Files under `<root>/state/` (today that directory holds `auth/` and almost nothing else) is a first implementation, not the contract. The JSON heartbeat file is the exhibit that this primitive is missing, not a storage design to keep.

## 3. Routing — a separate policy, iMessage-specific

Ledger and blackboard do not pick the crew. For iMessage, still one of:

| policy | behavior | failure mode |
|---|---|---|
| **last-outbound-wins** (short TTL) | reply to Red Alert's ping goes to Red Alert | two crews ping the same handle inside the TTL; last sender wins, which may be wrong |
| **`@red-alert …` prefix** | explicit, always works | trains the human; silent if forgotten |
| **both** | prefix wins when present; else last-outbound if warm; else today's session | more moving parts |
| **today (default)** | one session per chat-guid | the motivating bug |

Discord / gchat already multiplex by channel / space. Do not invent a second router there.

Last-outbound can be a tiny per-target index; it does **not** require the ledger. It *can* be a second `CommListener` that writes that index, or isaac-imessage can query the ledger module, or it can stamp its own. OPEN — see questions. Do not make routing a child of the ledger.

Collaboration pattern once 1+2 exist:

| | owns | writes when |
|---|---|---|
| domain crew (Red Alert) | the document | cron / its own turns |
| conversational crew (zane) | nothing | Micah says something that mutates a fact |
| the ledger module | the wire log | every listener fire it chooses to persist |

zane does not need to *be* Red Alert. He needs to update Red Alert's facts. Red Alert does not need to hear the reply. He needs the world to be true next time he wakes up.

# Modules / berths

**Two new modules. One new berth. Agent does not grow a log.**

| piece | home | contributes |
|---|---|---|
| listener berth + fire points + `:crew`/`:session` on enqueue | **isaac-agent** | berth `:isaac.agent/comm-listener` (declared; empty until a module fills it) |
| ledger (storage, retention, `comm_recent`) | **isaac-ledger** (new) | `:isaac.agent/comm-listener`, `:isaac.agent/tools`, optional `:isaac.config/schema` |
| blackboard (`state_get` / `state_put`) | **isaac-blackboard** (new) | `:isaac.agent/tools` only |
| inbound routing policy | **isaac-imessage** | extra-schema on the imessage slot; maybe a second listener for last-outbound |

**Berth, not a fourth comm.** Comms stay on `:isaac.server/comm`. Adapters stay pipes; they do not grow a ledger API. If this starts looking like a comm or a crew, the design has gone wrong.

No pluggable "ledger-backend" berth inside the ledger module. The *module* is the plug. Want a different store? Ship a different module on the same listener berth. Two listeners can coexist (ledger + last-outbound index + a test spy).

Pattern already in tree: hail contributes tools without living in the agent; `:isaac.agent/tools` / `:isaac.agent/slash-commands` are factory maps with a registry. This is that, for wire events.

# Track all comms?

**Agent fires all of them.** Writes happen where crew is known (`send!` success, inbound charge accept). Per-comm opt-in at the *agent* is a footgun ("why didn't my listener see this Discord ping?"). Cost of a callback is tiny next to an LLM turn.

The **ledger module** decides what to persist. CLI / ACP skip, heartbeat filtering, truncation — module policy, not an agent carve-out.

Still useful on Discord/gchat even though routing there is already solved: a crew *not* bound to that channel can query what was said. That's the cross-crew job.

Do not persist attachment bytes. Heartbeats are chatty (Red Alert × 96/day); retention is the module's problem or the log is a heartbeat dump.

# Configuration

Ledger logging is **on when the module is installed**. Failure to persist is a warn inside the module, not a failed send. Agent has no `:comm-ledger` key.

```clojure
;; isaac-ledger module schema (example — the module owns this table)
:comm-ledger {:retain-days 30
              :max-chars   2000}

;; blackboard: no config. Files exist when written. Ownership is the key prefix.

;; iMessage slot — routing only; omit = today's one-session-per-chat
:comms {:imessage {:type :imessage
                   :imessage/allow-from ["hieronymus@marigold.example"]
                   :imessage/inbound-route {:policy :both        ; :session | :last-outbound | :prefix | :both
                                            :ttl-seconds 600}}}
```

Tool grants stay the existing crew allow-list:

```clojure
:crew {:zane      {:tools {:allow [… :comm_recent :state_get :state_put]}}
       :red-alert {:tools {:allow [… :state_get]}}}
```

Do not grant `comm_recent` to every crew by default — it's a cross-crew window. Conversational crews yes; heartbeat crons probably no. Tools are absent until their module is on the classpath, so an install without isaac-ledger simply has no `comm_recent` to grant.

# Costs

| | pay | skip and you get |
|---|---|---|
| **Disk** | whatever the ledger module stores. Heartbeat volume is the real size driver, not Micah. | unbounded log of 15-minute pings |
| **Latency** | one listener callback on successful send (already async on the delivery worker) and on inbound accept | — |
| **Tokens** | none unless a crew calls `comm_recent` / `state_*`. Do not auto-inject. | context pollution, the thing we refused |
| **Queue schema** | stamp `:crew` / `:session` on enqueue so listeners have the crew column | outbound events with no crew — the gap the ledger exists to fill |
| **Races** | blackboard last-write-wins | lost updates on concurrent put; acceptable for alerts, document it |
| **Privacy** | any crew granted `comm_recent` can read other crews' pings to that target | that's the point; be honest in the handbook |
| **Wrong-crew turns** | routing policy | the motivating bug, paid in LLM cost + confusion every time |
| **Complexity** | one agent berth + two new modules + imessage routing. Risk is over-abstracting a file append. | storage baked into the agent, first implementation frozen |

# Grounded in current code (2026-10-02)

- `isaac.tool.comm-send/comm-send-tool` → `queue/enqueue!` → delivery worker `send!` → `queue/delete-pending!` on `:ok`. No log, no crew on the record, no listener.
- `isaac-imessage` `notification->work-item` returns nil on `:is_from_me`; session key `imessage:<chat-guid>`; `dispatch-work-item!` builds a charge with origin, no crew override.
- Charge schema already has `:crew` and `:origin` — inbound listener payload is sitting there.
- `memory_*` tools write `<root>/crew/<id>/memory/<date>.md` — crew-scoped, prose, wrong shape. They live in the agent; the blackboard should not copy that mistake.
- `<root>/state/` exists; contents are auth, not a blackboard. A blackboard module should not collide with `state/auth/`.
- `:isaac.agent/tools` already hosts module-contributed tools (hail: `hail-send`, `hail-get`). Blackboard is that pattern.
- Agent berths today: `:isaac.agent/tools`, `:llm-api`, `:slash-commands`, `:provider`, `:provider-template`. No observer/listener berth yet. Comm protocol is the wrong one to extend.
- isaac-imessage ROADMAP stretch: "per-chat crew/model overrides via config" — related, not this.

# Open questions for review

1. **Routing default.** Ship `:session` (today) and make last-outbound / prefix opt-in on the imessage slot? Or ship `:both` on zanebot because the bug is live? Lean: opt-in config, enable `:both` on zanebot as the first customer.
2. **What the agent fires vs what the ledger persists.** Lean: agent fires every origin-bearing inbound charge and every queued outbound `Comm/send!` (iMessage, Discord, gchat, gmail, attention). Skip CLI at the fire point (no queue record). ACP: fire if it went through `Comm/send!`, else skip. Ledger module may still drop heartbeats / ACP.
3. **Blackboard value shape.** EDN maps (queryable, crew-readable) vs opaque strings. Lean: EDN. One document per key, not a row store. Module's call.
4. **Ownership enforcement.** Convention (`state/<crew>/…`) vs tool-level write allowlist by prefix. Lean: convention first. A conversational crew *must* be able to write Red Alert's document or the gift-bought flow dies.
5. **Retention / heartbeat noise.** 30 days / 2000 chars — fine? Filter `:dir :out` from cron sessions in `comm_recent` default view so a human query isn't 96 heartbeats? Module's call.
6. **Prefix grammar.** `@red-alert rest` vs `/crew red-alert rest`. `@` is what Micah sketched. Crew ids with hyphens. Unknown crew: drop to default session, don't 404 the text.
7. **Implementation split.** Four beans, not three:
   1. isaac-agent: berth + fire points + crew/session on enqueue
   2. isaac-ledger (blocked by 1)
   3. isaac-blackboard (independent)
   4. isaac-imessage routing (independent of 2 and 3; last-outbound *may* want 1)
8. **Berth name.** `:isaac.agent/comm-listener` (literal) vs `:isaac.agent/wire` vs `:isaac.agent/comm-observer`. Lean: `comm-listener`.
9. **Module names.** isaac-ledger / isaac-blackboard vs isaac-comm-log / isaac-state. Lean: the first pair. `isaac-state` collides with `<root>/state/auth`.
10. **Last-outbound home.** Second `CommListener` (could live in isaac-imessage or isaac-ledger) vs imessage-internal vs query `comm_recent`. Lean: a tiny listener next to imessage, not a ledger query — routing must work if the ledger module is not installed.
11. **Blackboard vs `state/auth`.** Module should not write under `state/auth/`. Namespace the module's tree (`state/board/…` or `board/…`) so auth stays agent-owned.

# Deliberately out of scope

- Group chats, reactions, imessage per-chat crew overrides (already stretch on isaac-imessage).
- Injecting ledger or blackboard into compaction / recall / episodes.
- A receptionist crew, hail-as-blackboard, memory-as-blackboard.
- Locking, CRDT, or query language on the blackboard.
- Changing Discord/gchat inbound routing.
- A second berth for "blackboard backends." The blackboard *module* is the plug.

## Review outcome (2026-10-02, Micah + planner)

- Listener/observer berths and the ledger module: **deferred**, no immediate need.
- Outbound crew/session stamping: beaned as isaac-qn4o (todo, baselined).
- iMessage routing: beaned as isaac-ugvu (draft, opt-in, off by default, blocked by qn4o).
- Facts: Red Alert restructured without a blackboard module — bin/redalert keeps alerts.json (statuses open/handled/snoozed), ALERTS.md publishes the contract, zane's soul points at `redalert ack`.
