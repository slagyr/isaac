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
updated_at: 2026-10-02T14:19:22Z
---

# Status

Draft for review. Not actionable. No scenarios yet. Split into child features only after this design is accepted or rewritten.

Motivating incident (2026-10-01): Red Alert pinged Micah over iMessage about Leo's birthday gift. Micah replied in the same Apple thread. The reply landed on zane, who had no Red Alert context and patched `heartbeat-state.json` by hand. That is a workaround, not routing.

Conversation: zane + Micah, 2026-10-01 → 2026-10-02.

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

# Proposed solution

One line: **log the wire, share the facts, route the inbound.**

## 1. The wire — a comm ledger, not a transcript

Queryable log of what crossed a comm, with the column Apple does not have: **which crew**.

```clojure
{:at      "2026-10-01T14:47Z"
 :comm    :imessage
 :dir     :out
 :crew    :red-alert
 :session "heartbeat"
 :target  "micahmartin@mac.com"
 :text    "Leo's birthday is in 3 days — might want to grab something!"}
```

**Where it is written** (not the adapters, not the bridge's send path):

- **Out** — delivery worker, on successful `Comm/send!`. That is the first moment we know it actually left. Today the queue record has `:comm :target :content :id :attempts :created-at` and **no crew/session** — those must be stamped at `enqueue!` (from `comm_send` / cron origin).
- **In** — bridge, when it accepts a comm charge. That is the first moment inbound has a crew.

`isaac-imessage` / `isaac-discord` stay pipes. They do not know crew. The bridge never sees `comm_send`. Tracking belongs in **isaac-agent's comm domain** — same neighborhood as the delivery queue, different job.

**How crews read it:** tool `comm_recent` (last N, filterable by comm / target / dir / crew). On demand. Not in the system prompt.

## 2. Facts — a blackboard, not memory, not hail, not the ledger

Named documents other crews can mutate. Linda tuplespace, not a database.

```
<isaac-root>/state/red-alert/alerts.edn
```

Red Alert owns it (convention, not a lock). Conversational crew writes `{:birthday :Leo :year 2026 :status :done}` when Micah says the gift is bought. Next heartbeat reads it and stays quiet.

**Tools:** `state_get` / `state_put` on namespaced keys. Files under `<root>/state/` (today that directory holds `auth/` and almost nothing else). Whole-document put, last-write-wins, callers do their own read-modify-write. No merge, no etag, no lock — enough for an alerts list, not for accounting.

The JSON heartbeat file is the exhibit that this primitive is missing, not a storage design to keep.

## 3. Routing — a separate policy, iMessage-specific

Ledger and blackboard do not pick the crew. For iMessage, still one of:

| policy | behavior | failure mode |
|---|---|---|
| **last-outbound-wins** (short TTL) | reply to Red Alert's ping goes to Red Alert | two crews ping the same handle inside the TTL; last sender wins, which may be wrong |
| **`@red-alert …` prefix** | explicit, always works | trains the human; silent if forgotten |
| **both** | prefix wins when present; else last-outbound if warm; else today's session | more moving parts |
| **today (default)** | one session per chat-guid | the motivating bug |

Discord / gchat already multiplex by channel / space. Do not invent a second router there.

Last-outbound can be a tiny per-target index; it does **not** require the full ledger. Do not make routing a child of the ledger.

Collaboration pattern once 1+2 exist:

| | owns | writes when |
|---|---|---|
| domain crew (Red Alert) | the document | cron / its own turns |
| conversational crew (zane) | nothing | Micah says something that mutates a fact |
| the bus | the ledger | every send/receive |

zane does not need to *be* Red Alert. He needs to update Red Alert's facts. Red Alert does not need to hear the reply. He needs the world to be true next time he wakes up.

# Modules / berths

**No new modules.** These are two tools and a file log, not a product surface.

- Ledger + `comm_recent` → **isaac-agent** comm domain (`isaac.comm.delivery.*`, plus a log ns). Same place we already decided comms live (isaac-6pqo).
- Blackboard + `state_get` / `state_put` → **isaac-agent** tools, files under `<root>/state/`.
- Routing → **isaac-imessage** inbound (`notification->work-item` / dispatch) plus, if last-outbound, a small agent-side lookup. Per-chat crew overrides are already a stretch goal in isaac-imessage's ROADMAP; this is a different axis (who, not which chat).

**No new berths.**

- Tools already contribute on `:isaac.agent/tools`. `comm_recent`, `state_get`, `state_put` are three more entries there, next to `comm_send` and `memory_*`.
- Comms already contribute on `:isaac.server/comm`. Adapters stay pipes; they do not grow a ledger API.
- A pluggable ledger-backend or blackboard-store berth is YAGNI. File log, same as the delivery queue (`<root>/comm/delivery/`).

If this starts looking like a fourth comm or a crew, the design has gone wrong.

# Track all comms?

**Recommendation: yes, at the agent seams, automatically.** Writes happen where crew is known (`send!` success, inbound charge accept). Per-comm opt-in is a footgun ("why isn't this Discord ping in the log?"). Cost of a JSONL line is tiny next to an LLM turn.

Still useful on Discord/gchat even though routing there is already solved: a crew *not* bound to that channel can query what was said. That's the cross-crew job.

**Carve-out to decide:** CLI / ACP already have session transcripts. Logging every ACP prompt into the ledger may be noise. Lean: log origin-bearing inbound charges and queued outbound `Comm/send!` (iMessage, Discord, gchat, gmail, attention). Skip CLI. ACP: skip unless someone has a cross-crew need.

Do not log attachment bytes. Truncate text. Heartbeats are chatty (Red Alert × 96/day); retention is mandatory or the log is a heartbeat dump.

# Configuration

Ledger is **always on** once shipped — routing forensics and `comm_recent` both need the writes even if no crew is granted the tool. Failure to append is a warn, not a failed send.

```clojure
;; optional agent-level (defaults shown)
:comm-ledger {:retain-days 30
              :max-chars   2000}

;; iMessage slot — routing only; omit = today's one-session-per-chat
:comms {:imessage {:type :imessage
                   :imessage/allow-from ["micahmartin@mac.com"]
                   :imessage/inbound-route {:policy :both        ; :session | :last-outbound | :prefix | :both
                                            :ttl-seconds 600}}}
```

Blackboard: **no config.** Files exist when written. Ownership is the key prefix (`state/<crew>/…`).

Tool grants stay the existing crew allow-list:

```clojure
:crew {:zane      {:tools {:allow [… :comm_recent :state_get :state_put]}}
       :red-alert {:tools {:allow [… :state_get]}}}
```

Do not grant `comm_recent` to every crew by default — it's a cross-crew window. Conversational crews yes; heartbeat crons probably no.

# Costs

| | pay | skip and you get |
|---|---|---|
| **Disk** | JSONL, truncated text, 30-day retain. Heartbeat volume is the real size driver, not Micah. | unbounded log of 15-minute pings |
| **Latency** | one file append on successful send (already async on the delivery worker) and on inbound accept | — |
| **Tokens** | none unless a crew calls `comm_recent` / `state_*`. Do not auto-inject. | context pollution, the thing we refused |
| **Queue schema** | stamp `:crew` / `:session` on enqueue; today those fields do not exist | outbound rows with no crew column — the gap the ledger exists to fill |
| **Races** | blackboard last-write-wins | lost updates on concurrent put; acceptable for alerts, document it |
| **Privacy** | any crew granted `comm_recent` can read other crews' pings to that target | that's the point; be honest in the handbook |
| **Wrong-crew turns** | routing policy | the motivating bug, paid in LLM cost + confusion every time |
| **Complexity** | three small features. Risk is a worker bundling them. | one muddy module |

# Grounded in current code (2026-10-02)

- `isaac.tool.comm-send/comm-send-tool` → `queue/enqueue!` → delivery worker `send!` → `queue/delete-pending!` on `:ok`. No log, no crew on the record.
- `isaac-imessage` `notification->work-item` returns nil on `:is_from_me`; session key `imessage:<chat-guid>`; `dispatch-work-item!` builds a charge with origin, no crew override.
- `memory_*` tools write `<root>/crew/<id>/memory/<date>.md` — crew-scoped, prose, wrong shape.
- `<root>/state/` exists; contents are auth, not a blackboard.
- `:isaac.agent/tools` berth already hosts `comm_send`. `:isaac.server/comm` is the adapter berth.
- isaac-imessage ROADMAP stretch: "per-chat crew/model overrides via config" — related, not this.

# Open questions for review

1. **Routing default.** Ship `:session` (today) and make last-outbound / prefix opt-in on the imessage slot? Or ship `:both` on zanebot because the bug is live? Lean: opt-in config, enable `:both` on zanebot as the first customer.
2. **All comms vs origin-bearing only.** Recommendation above; confirm CLI/ACP skip.
3. **Blackboard value shape.** EDN maps (queryable, crew-readable) vs opaque strings. Lean: EDN. One document per key, not a row store.
4. **Ownership enforcement.** Convention (`state/<crew>/…`) vs tool-level write allowlist by prefix. Lean: convention first. A conversational crew *must* be able to write Red Alert's document or the gift-bought flow dies.
5. **Retention / heartbeat noise.** 30 days / 2000 chars — fine? Filter `:dir :out` from cron sessions in `comm_recent` default view so a human query isn't 96 heartbeats?
6. **Prefix grammar.** `@red-alert rest` vs `/crew red-alert rest`. `@` is what Micah sketched. Crew ids with hyphens. Unknown crew: drop to default session, don't 404 the text.
7. **Implementation split.** If this stands, three child features (ledger, blackboard, imessage route), no shared milestone required. Routing can land without the other two; blackboard can land without the ledger.

# Deliberately out of scope

- Group chats, reactions, imessage per-chat crew overrides (already stretch on isaac-imessage).
- Injecting ledger or blackboard into compaction / recall / episodes.
- A receptionist crew, hail-as-blackboard, memory-as-blackboard.
- Locking, CRDT, or query language on the blackboard.
- Changing Discord/gchat inbound routing.
