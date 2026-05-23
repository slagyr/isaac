---
# isaac-xzq6
title: 'Hail crew tool: LLM-invokable hail/send from inside a turn'
status: draft
type: feature
priority: normal
created_at: 2026-05-23T21:57:41Z
updated_at: 2026-05-23T22:05:40Z
parent: isaac-ugx7
blocked_by:
    - isaac-vduq
---

## Motivation

Slice 5a of the Hail epic. The substrate (isaac-vduq) ships
`hail.queue/send!` as a library function and `isaac hail send` as
a CLI. This bean exposes the same capability as a **crew tool** —
an LLM in a turn can invoke `(hail-send ...)` to dispatch hails
from inside its reasoning loop.

Enables agent-to-agent communication: a planner crew member sends
a `bean.verify.requested` hail when finished; a worker crew member
sends `worker.42.ci-result` on completion.

## Scope

### Tool registration

Add a tool definition in `src/isaac/tool/hail.clj` (or similar)
exposing `hail-send` (or `send-hail`) to crews that allow it. Tool
schema mirrors the library function's argument shape:

```json
{
  "type": "function",
  "function": {
    "name": "hail-send",
    "description": "Send a hail to a frequency.",
    "parameters": {
      "frequency": "<address-map>",
      "payload":   "<edn-value>",
      "prompt":    "<string>"
    }
  }
}
```

### Invocation flow

1. LLM in a turn produces a tool call `hail-send` with EDN args
2. Tool handler parses the args, builds a hail record, calls
   `isaac.hail.queue/send!`
3. `:from` is set to `:crew/<name>` (crew identity, not `:cli`)
4. Returns the hail id back to the LLM as the tool result

### Allow-list

Per existing tool conventions in `:tools.allow`, crews opt in to
`hail-send` by listing it. Default: NOT allowed (operator must
explicitly enable, since LLM-driven hail sends can cascade).

## Out of scope (deferred)

- **Loop-detection safeguards** beyond the existing tool-loop
  iteration cap. If hail-send-driven turns recurse uncontrollably,
  the cap kicks in; operator follows up with `:max-loops` or
  per-tool rate limits.
- **Hail-receive tool** for inspecting inbox from inside a turn.
  Turns already get deliveries via initial context; a query tool
  isn't needed for v1.

## Acceptance

- `hail-send` tool registered and available when listed in crew's
  `:tools.allow`.
- Tool handler accepts address map + payload + prompt, calls
  `send!`, returns the hail id.
- Sender identity (`:from`) captures the calling crew.
- Crews without `hail-send` in allow list can't invoke it.

## Feature scenarios

`features/hail/crew-tool.feature`, `@wip`. To draft later:

- Crew allows hail-send → can dispatch a hail in a turn.
- Crew doesn't allow hail-send → tool unavailable / errors.
- Sent hail's `:from` records the calling crew identity.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-vduq (substrate)** — uses
  `isaac.hail.queue/send!`.
- **Independent of fan-out / wake** — this bean only produces;
  delivery is downstream's job.
