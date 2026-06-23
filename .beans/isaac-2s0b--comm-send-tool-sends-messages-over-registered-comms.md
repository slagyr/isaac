---
# isaac-2s0b
title: comm_send tool sends messages over registered comms
status: todo
type: feature
priority: normal
created_at: 2026-06-23T17:10:38Z
updated_at: 2026-06-23T22:03:56Z
---

Isaac already knows how to deliver outbound messages once a delivery record exists: registered comm instances implement `Comm/send!`, and the persistent delivery queue stores records shaped like `{:comm ... :target ... :content ...}`. What Isaac does not have is a crew-callable tool that lets a model create one of those deliveries on purpose.

That gap blocks obvious workflows: send a note to the harbor bell Discord channel, iMessage Cordelia that the lantern is lit, or hand off a result to a specific operator without forcing them to poll a session.

## Existing building blocks

- `isaac.agent/tools` already exposes LLM-callable tools and crews opt into them via `:tools.allow`.
- `isaac.comm.delivery.queue/enqueue!` already persists outbound deliveries and the worker already handles retry / dead-letter behavior.
- Registered comm instances live under `:isaac.server/comm`, and the queue worker already resolves `:comm` to a live instance before calling `Comm/send!`.
- Discord and iMessage already support outbound send with the current record shape (`:target`, `:content`, and optional extra fields like iMessage `:service`).
- `hail-send` is a working precedent for "LLM tool creates an outbound delivery record", but only for the hail substrate.

## Gap

- No generic tool turns a model's intent into an outbound comm delivery.
- No shared contract exists for comm-specific addressing extras.
- No outbound safety policy exists yet for "which comms / targets may a model message?"

## Proposed direction

- Add a `comm_send` tool to `:isaac.agent/tools`.
- The tool should enqueue a delivery record; it should not call `Comm/send!` inline. Queue-first keeps durability, retry, and dead-letter semantics consistent with every other outbound path.
- The tool should address a configured comm slot, not just a raw comm type. A slot id binds real credentials and local policy (`:dock-discord`, `:harbor-phone`) in a way `:discord` / `:imessage` alone does not.
- Start with explicit addressing: the caller names the comm slot, target, and content. "Reply on the current comm/channel" can be a follow-up if it proves important.
- MVP should stay plain-text and reuse the delivery-record shape as much as possible.

## Design questions to settle before promotion to todo

1. Argument shape: is the public tool contract `comm`, `target`, `content`, plus a narrow optional metadata map, or do we want a richer address object from day one?
2. Guardrails: how do we stop arbitrary outbound spam? Likely candidates are per-crew allowlists, per-comm allowlists, target-pattern allowlists, or some combination.
3. Comm discovery: should the tool accept only configured comm slot ids, or also allow direct comm-type addressing when exactly one slot of that type exists?
4. Result contract: should the tool return the queued delivery id immediately, or try a synchronous first send? Queue-id return feels like the right default.
5. Unsupported comms: how should null/CLI/ACP behave if referenced? Most likely as permanent tool errors with no queue side effect.
6. Metadata scope: which comm-specific extras are allowed in MVP (for example iMessage `:service`), and how are they validated?

## Acceptance sketch

- A crew with `comm_send` in `:tools.allow` can queue a Discord message to a specific configured channel and receives the queued delivery id.
- A crew with `comm_send` in `:tools.allow` can queue an iMessage to a specific recipient handle and the delivery record carries any required iMessage metadata.
- Unknown comm slot ids fail as tool errors without enqueueing.
- Malformed or unauthorized targets fail as tool errors without enqueueing.
- Delivery continues through the normal queue / worker path, including transient retry and permanent dead-letter behavior.

## Notes

- This bean should remain `draft` until `/plan-with-features` writes and commits the `@wip` scenarios.
- Related: `isaac-8xb7` (scheduled reminders delivered to a comm) wants the same outbound seam and should likely depend on this or share its delivery contract.


## Design settled (2026-06-23): send-schema owns addressing; comm+content common (model B)

Empirical check of send! impls: only **discord** (:target->channel-id) and **imessage** (:target->to-handle, plus :service extra) read :target. cli/null/telly are no-op outbound ({:ok false}); memory stores the whole record. The queue/worker do NOT mandate :target — enqueue! stores arbitrary keys and the worker just hands the record to send!. The only structurally-required field is :comm.

So hardcoding target/content as universal blocks the extensibility :send-schema is meant to provide. Settled model (B):
- **Common (hardcoded) params:** :comm (required; enum of configured comm slot ids) and :content (required — a message without content isn't useful).
- **Per-comm params:** :target and any extras (e.g. imessage :service, telly :loft) come from the SELECTED comm's :send-schema. NO normalizer — send-schema field NAMES are the record keys, written verbatim.
- **Tool schema** = comm + content + the UNION of all configured comms' :send-schema fields, each OPTIONAL (required-ness enforced at tool-exec against the chosen comm's send-schema).
- **Record written** = {:comm <slot> :content <body> ...send-schema fields}.
- :send-schema is the COMPLETE per-comm outbound contract; a new comm adds outbound fields with ZERO isaac-agent changes.

Supersedes the earlier "canonical record shape {:comm :target :content}" framing: the canonical record is {:comm <slot> :content <body>} + comm-declared fields.


## Decision (2026-06-23): :send-schema keys MUST be namespaced by comm type

To avoid key collisions in the union tool schema (two comms both declaring e.g. target), every :send-schema key is namespaced by the comm TYPE:
- telly:    :telly/target, :telly/loft
- discord:  :discord/target
- imessage: :imessage/target, :imessage/service
:comm stays the slot id (the enum); :content stays the common un-namespaced field. The union then has zero collisions — every per-comm param is globally unique.

Consequence (no normalizer): the namespaced keys ARE the record keys, so each comm's send! reads its own namespaced key + the common :content. RIPPLE / scope:
- discord send!: (:target record) -> (:discord/target record)
- imessage send!: (:target record)/(:service record) -> (:imessage/target record)/(:imessage/service record)
- every OTHER writer of these records (esp. the turn->comm reply-enqueue path) must emit the namespaced per-comm keys too, so there is ONE record contract.
(Open: could limit namespacing to extras only and keep :target un-namespaced/common — less invasive but reopens the 'is target common' question. Current direction: full namespacing.)

JSON: namespaced keyword :telly/target <-> JSON property "telly/target"; comm_send does (keyword "telly/target") -> :telly/target when writing the record.


## Namespacing rule (2026-06-23, settled): framework bare, module keys namespaced

- Framework-owned record/param keys are BARE: :comm (slot id), :content (message body).
- ANY key contributed by a module (i.e. every :send-schema field) MUST be namespaced by the comm type: :telly/target, :telly/loft, :discord/target, :imessage/target, :imessage/service.
- Full-namespacing confirmed (target included). discord/imessage send! + the reply-enqueue path migrate to the namespaced keys (one record contract).
- NOTE for the new "a pending comm delivery matches:" step (and any EDN path matcher used): a namespaced keyword like :telly/target is a SINGLE top-level key, not a nested path telly->target. The path syntax/matcher must treat "telly/target" as the keyword :telly/target.


## Scenarios written + locked (2026-06-23)

Feature file: `isaac-agent/features/tool/comm_send.feature` (4 @wip scenarios):
1. bare comm (skybeam, no :send-schema) -> comm_send params are exactly {comm, content}.
2. a comm with :send-schema (telly: :telly/target, :telly/loft) -> params gain those namespaced fields (optional).
3. calling comm_send (toolCall args incl namespaced keys) -> a pending comm delivery matches {comm, content, telly/target, telly/loft}; queue-first (lands in pending/, worker never ticked).
4. unknown comm slot (phantom) -> tool result is an error; no pending deliveries.

New steps owed by the implementer:
- `the prompt tool "<name>" has parameters:` (exact param set; namespaced keys are single keyword keys).
- `a pending comm delivery matches:` (id-agnostic).
- `there are no pending comm deliveries`.
- `the comm_send tool result is an error` (verify an existing :tool-result step doesn't already cover it).

In-scope for THIS bean (the generic mechanism + fixture): add a `:send-schema` field to the `:isaac.server/comm` berth (analogous to `:extra-schema`); give the **telly fixture** a `:send-schema {:telly/target ... :telly/loft ...}`; build comm_send to compose its schema (common comm+content + union of configured comms' namespaced send-schema) and write the record verbatim, queue-first via `queue/enqueue!`.

Deferred to follow-up (isaac-<see below>): real comm modules (discord/imessage) adopting `:send-schema` + migrating send! to namespaced keys, and the turn->comm reply-enqueue path emitting namespaced keys.

Still OPEN (not yet specced): outbound guardrails / spam policy (per-crew or per-comm allowlists). Suggest a separate bean; scenario 4 only covers unknown-slot errors, not authorization.
