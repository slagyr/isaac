---
# isaac-2s0b
title: comm_send tool sends messages over registered comms
status: draft
type: feature
created_at: 2026-06-23T17:10:38Z
updated_at: 2026-06-23T17:10:38Z
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
