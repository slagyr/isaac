---
# isaac-ugx7
title: 'Hail: pub/sub message bus for crew and external producers'
status: todo
type: epic
created_at: 2026-05-22T22:44:57Z
updated_at: 2026-05-22T22:44:57Z
---

## Motivation

Isaac's crews currently can't talk to each other or react to external
events asynchronously. Five concrete scenarios drive the design:

1. **Bean ready → workers compete.** Beans CLI emits when a new bean is
   ready to work. One worker (and exactly one) picks it up.
2. **Library updated → all subscribers notified.** A change on project
   X is broadcast; every crew that cares about X gets a copy.
3. **CI fails → notify the responsible worker.** CI emits to a frequency
   addressed at worker 42; only worker 42 receives.
4. **Bean done → verify picks it up.** Worker announces a bean is ready
   for verification; exactly one verify listener takes it.
5. **Verify fails → notify submitter.** Verifier emits at the original
   worker; only they receive.

The pattern: a small pub/sub bus with two delivery modes and external
producers. Name is **Hail** — Trek's "hailing frequencies" maps
directly, fits the existing spaceship vocabulary (crew, bridge, berths,
comm, charge), and distinguishes from `comm` (external channels) and
`bridge` (turn dispatch).

## Vocabulary

- **Hail** — the subsystem (and a noun for a single message).
- **Frequency** — a topic name. NATS-style patterns: `*` = one
  segment, `>` = remaining segments. Examples: `beans.ready`,
  `worker.42.>`, `project.libX.deps`.
- **Subscription** — a central routing rule pairing a frequency with
  listener requirements (crew tags, session tags) and a delivery
  mode/group. Lives once globally — frequency contracts are shared,
  not owned by any one crew.
- **Crew-tags** — tags a crew must carry to be a candidate listener
  for a subscription.
- **Session-tags** — tags that filter which session of a candidate
  crew handles the hail. If no matching session exists, a new one is
  created tagged with these.
- **Mode** — `:fanout` (every matching crew gets one delivery) or
  `:queue` with a `:group` (one matching crew per group wins).
- **Listener** — the (crew, session) pair selected for delivery.
- **Producer** — anything that sends hails: a crew tool, the CLI, an
  HTTP endpoint on the server.
- **Send** — produce a hail on a frequency.

The crew is the matching unit; session is selected after the crew
matches. Fanout fires one delivery per matching crew, not per session.

Tagging is the underlying capability — both crews and sessions carry
flat tag sets (keyword tags, namespaced or not). Tagging itself lives
in a sibling epic; Hail depends on it.

Direct addressing (scenarios 3, 5) falls out: a subscription whose
`:crew-tags` matches exactly one crew (e.g., that crew's unique name
tag) delivers only to that crew.

## Architecture

### Storage (filesystem + EDN, same shape as `comm/delivery/`)

```
<state-dir>/hail/
├── pending/<hail-id>.edn               # newly sent, awaiting fan-out
├── subscriptions/<sub-id>.edn          # {:frequency :listener :mode :group?}
└── deliveries/<listener-id>/inbox/<delivery-id>.edn
```

### Flow

1. **Send** — producer atomically writes `pending/<hail-id>.edn`
   (tempfile + rename). Hail record:
   `{:id :frequency :payload :from :sent-at}`.
2. **Fan-out worker** ticks (~1s on the shared scheduler, parallel to
   cron's worker): for each pending hail, walk subscriptions whose
   frequency pattern matches and that haven't already been
   delivered-to for this hail:
   - **Candidate crews** — crews whose tags include all `:crew-tags`.
     If empty, this subscription waits (no delivery this tick).
   - **Mode selection** — `:fanout` → deliver to every candidate
     crew; `:queue :group "G"` → pick one candidate crew (least-busy
     → newest-active → arbitrary) per group.
   - **Session selection** (per chosen crew) — find sessions of
     that crew whose tags include all `:session-tags`. If at least
     one is idle, use it (prefer most-recent idle). If all matching
     sessions are busy AND the crew is under `:max-concurrent`,
     create a new session tagged with `:session-tags`. If no
     matching sessions exist AND the crew is under `:max-concurrent`,
     create likewise. If the crew is at `:max-concurrent`, defer —
     the hail stays in pending for next-tick retry. Uses Isaac's
     existing default of creating a session when none is specified.
     Session in-flight state and `:max-concurrent` enforcement come
     from isaac-a1nu (crew concurrency).
   - **Write the delivery** to that (crew, session)'s inbox and
     record the subscription as delivered for this hail (tracked
     inline in the pending record).

   When all matching subscriptions for a hail have been delivered to,
   delete the pending file. Subscriptions with no candidate crews
   stay un-delivered and are retried on the next tick — the system
   is self-healing: add a crew with matching tags later and pending
   hails dispatch.

   Single bus worker → no multi-writer CAS dance.
3. **Wake** — same scheduler also polls inboxes. When
   `deliveries/<crew>/inbox/` is non-empty AND the crew has no
   in-flight turn, build a charge and `bridge/dispatch!` (exact same
   shape `cron/service.clj` uses today). The dispatched turn drains
   its inbox into the opening prompt.
4. **Ack semantics (v1)** — auto-ack on turn start. Inbox files move
   to `read/` (or get deleted). If the turn crashes, the hail is
   lost. Acceptable for v1; promote to explicit-ack if a scenario
   demands at-least-once with retry.

### Subscription registry

Subscriptions live centrally in the system config under
`:hail/subscriptions`, parallel to how `:cron` jobs are configured
today (declarative, reload-friendly). Crews do not declare their own
listening — frequency contracts (mode, group, session-tag semantics)
are shared, and per-crew declarations would let each listener own a
piece of a frequency's contract, risking inconsistency between two
crews on the same frequency.

```clojure
:hail {:subscriptions
       [{:frequency     "project.chess.bean.ready"
         :crew-tags     [:role/worker]
         :session-tags  [:project/chess]
         :mode          :queue
         :group         "chess-workers"}

        {:frequency     "project.chess.bean.ready"
         :crew-tags     [:role/dashboard]
         :session-tags  []
         :mode          :fanout}

        {:frequency     "worker.42.>"
         :crew-tags     [:name/worker-42]
         :session-tags  []
         :mode          :fanout}]}
```

Multiple subscriptions on the same frequency are allowed — they
represent different listener types that all want to know (e.g.,
workers compete via queue-group, dashboards log via fanout).

Materializes into `subscriptions/<sub-id>.edn` via a `Reconfigurable`
module. Adding/removing from config → adds/removes the sub file
through the live registry.

### Producers

All three converge on the same `hail.queue/send!`:

| Surface   | How                                                              |
|-----------|------------------------------------------------------------------|
| Crew tool | `hail` tool: `(hail "<frequency>" <edn-payload>)`          |
| CLI       | `isaac hail send <frequency> <edn-payload>`                    |
| HTTP      | `POST /hail/send` on the existing server                       |

## Design choices made

1. **Two modes, not three.** Fanout + queue-group cover all five
   scenarios. Direct addressing is just a subscription whose
   `:crew-tags` match exactly one crew. Fewer primitives → fewer
   concepts to teach the LLM and the operator.
2. **Frequency patterns, NATS-style.** Well-known shape, cheap to
   match (split on `.`, walk). Allows `worker.42.>` without
   enumerating every subtype.
3. **Central subscription registry.** Subscriptions live once in
   `:hail/subscriptions`, not per-crew. Frequency contracts (mode,
   group, session-tag semantics) are shared and shouldn't be
   redefined by each listener. Per-crew opt-in would let two crews
   declare conflicting rules on the same frequency.
4. **Crew is the matching unit; session is selected after.**
   Subscriptions filter crews via `:crew-tags`, then pick a session
   of the matched crew (filtered by `:session-tags`, or created
   tagged with them). Fanout fires per-crew, not per-session. The
   alternative — pure tag algebra (union-and-gap, no crew/session
   distinction) — is more elegant but produces ambiguous edge cases
   (e.g., what does it mean to put a `:role/*` tag on a newly
   created session?). Explicit crew/session split removes the
   ambiguity; tag algebra remains a future option (see Open).
5. **Single fan-out worker.** Avoids the rename-as-CAS dance that
   multi-writer EDN-on-disk would require. Matches
   `comm/delivery/worker.clj`.
6. **Subscriptions in config, reload via Reconfigurable.** Parallel
   to cron. Declarative, no runtime API needed for v1.
7. **Default session-resolution: match-or-create, idle-first,
   capacity-gated.** Pick an existing session of the chosen crew
   matching `:session-tags`, preferring idle over busy. Otherwise
   create a new session for that crew tagged with `:session-tags`.
   Creation (and dispatch to an idle session) is gated by the crew's
   `:max-concurrent`; over capacity → defer and retry next tick. The
   in-flight signal and capacity check come from isaac-a1nu (crew
   concurrency). Richer policies are deferred (see Open).
8. **Same-install only for v1.** Cross-install `frequency@host`
   routing waits — the existing `comm` HTTP substrate already gives
   us the transport when we want it.
9. **Auto-ack v1.** Cheapest correct behavior. Crash-loss is real
   but acceptable until a scenario demands at-least-once.

## Slices (children of this epic)

1. **Substrate**: `hail.queue` with `send!` + `pending/` dir +
   atomic writes. Producer side only, no fan-out yet.
2. **Subscriptions & matcher**: `:hail/subscriptions` central
   config slice, `Reconfigurable` module to materialize
   subscription files, crew/session tag matcher. Depends on the
   tagging epic.
3. **Fan-out worker**: tick the pending dir, walk matching
   subscriptions, candidate-crew selection by `:crew-tags`,
   mode-aware delivery (fanout per crew, queue-group selection),
   session selection by `:session-tags` (match-or-create).
4. **Wake integration**: inbox poller + idle detection + charge
   dispatch + inbox-drain on turn start.
5. **Producers**:
   - 5a. `hail` crew tool
   - 5b. `isaac hail send` CLI
   - 5c. `POST /hail/send` route
6. **Frequency patterns**: NATS-style matcher (depends on slice 2).
7. **Primitive feature scenarios**: Gherkin under `features/hail/`
   covering the primitives — send writes to pending, fan-out
   delivers per mode, candidate-crew matching, session
   match-or-create, frequency pattern matching, wake on inbox.

## Open / deferred

- **Cross-install hails.** `frequency@host` scheme is a follow-up.
- **Explicit ack.** v1 is auto-ack; promote when a scenario demands
  at-least-once.
- **Richer session-resolution policies.** v1 ships only
  match-or-create. Other policies (singleton, ephemeral,
  fanout-across-sessions) added when scenarios demand. Likely an
  additive `:session-policy` field on subscriptions.
- **Tag algebra (Model 2).** A pure union-and-gap matcher with no
  crew/session distinction is more elegant but leaks at the edges
  (newly-created sessions tagged with role tags, untagged-crew
  bootstrap ambiguity, etc.). Could be added later as an alternative
  matcher, swappable per subscription or globally.
- **Hail TTL and dead-lettering.** v1 keeps undelivered hails in
  `pending/` indefinitely. Optional TTL field and a `failed/` dir
  for audit when subscriptions persistently fail to match.
- **Non-crew listeners.** Other Isaac subsystems (verifier, future
  indexer) may want to consume hails without being a crew. Out of
  scope v1.
- **Retention.** Delivered hails deleted by default; add an audit
  log if needed.

## Acceptance

This epic is "done" when slices 1–7 each have a child bean and slice
1 (substrate) is implementable without surprises. Children carry
their own closure conditions; they don't all need to land before
this epic is closed.

**Depends on the tagging epic** (separate sibling) — slice 2
(subscription matcher) can't ship until crews and sessions can carry
tags.

**Depends on isaac-a1nu (crew concurrency)** — slice 4 (wake
integration) needs session in-flight observability and
`can-dispatch?` for idle-first selection and capacity gating.

**Supersedes isaac-gw5** (inter-session messaging) — gw5's scope is
a strict subset (direct addressing only) and is folded into the
crew-targeted subscription pattern here. gw5 is being reframed as a
child of this epic for the `hail` crew tool surface (slice 5a).
