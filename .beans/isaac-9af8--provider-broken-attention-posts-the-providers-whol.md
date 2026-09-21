---
# isaac-9af8
title: 'Provider-broken attention posts the provider''s whole stream: 2.8 MB to Discord'
status: in-progress
type: bug
priority: high
tags:
    - ops
    - comm
created_at: 2026-09-21T04:46:42Z
updated_at: 2026-09-21T21:47:53Z
---

`provider-content` (isaac-agent `src/isaac/attention.clj:46`) appends the
provider's raw message with no bound:

```clojure
(when message message)
```

When a provider stream drops, that message is everything buffered — for the
claude CLI, the `system/init` event and the rest of the stream.

## Observed 2026-09-21 04:31Z (zanebot → Discord)

    Provider claude is broken model claude-opus-5 session 2026-06-29-1749-iaqu
    {"type":"system","subtype":"init","cwd":"/","session_id":"00a7ff11-…",
     "tools":["mcpisaaccommsend","mcpisaacexecrun", … ],
     "slash_commands":["deep-research","design-sync", … 
    … truncated 2844251 bytes

**2.8 MB** to a chat channel. Discord truncated it. The one fact an operator
needs — which provider broke and why — is in the first ~200 characters, with
megabytes of tool and slash-command listings after it.

What actually failed was a dropped stream, not a usage limit:

    04:31:24.071  tool/execute-failed  (bb features … timeout)
    04:31:24.296  chat/error  :status nil  :error :llm-error  :provider "claude"
    04:31:24.461  turn/model-response-summary  :tool-calls-count 0
    04:31:24.498  turn/ended  :ended-by :suspended

`:status nil` is the signature in isaac-9gcs (draft).

## Work

Cap the provider message in the attention content. An alert is a summary, not a
transport for a payload: keep a head of it, say how much was dropped, and leave
the full text to the log.

Worth deciding in the same pass: whether `enqueue-attention!` should cap **all**
attention content, not only this one call site — any future caller can make the
same mistake.

## Acceptance

- a provider-broken attention whose provider message is megabytes posts a
  bounded message; the cap is a named constant, not a magic number
- the message says how many characters were dropped
- provider, model and session still lead the content and are never truncated away
- a short provider message is unchanged (no ellipsis, no "0 dropped")
- the full untruncated text is still available in the log

## Related

isaac-9gcs — the dropped stream itself (`:status nil`) burning a hail attempt
instead of deferring. This bean is only about what gets posted when it happens.


## CORRECTION (2026-09-21): the premise above is wrong

Attention content **is** capped. `attention.clj:22` `clip-content` truncates to
`content-cap` (1000) and `enqueue-attention!` applies it to every call site,
`provider-content` included (:96).

The `… truncated 2844251 bytes` in the Discord message is **Isaac's own
truncation notice** — `clip-content`'s format string, verbatim. Discord received
roughly 1 KB. The safeguard worked; I misread it as its absence.

## What is actually wrong

The 1000 characters that survive are the **least** useful 1000. The provider's
message begins with the claude CLI's `system/init` event, so an operator sees:

    Provider claude is broken model claude-opus-5 session 2026-06-29-1749-iaqu
    {"type":"system","subtype":"init","cwd":"/","session_id":"…",
     "tools":["mcpisaaccommsend","mcpisaacexecrun", …
     "slash_commands":["deep-research","design-sync", …
    … truncated 2844251 bytes

Provider, model and session lead correctly. Everything after is a tool and
slash-command inventory. **Why** the provider broke is not in the window —
the real signal (`:status nil`, a dropped stream) never reaches the alert.

A head-of-string clip assumes the head is informative. For a streamed provider
error it is the handshake, and the failure is at the tail.

## Revised work

Make the alert carry the diagnosis rather than the transcript head. Options to
weigh: prefer the structured error (`:error`, `:status`) over the raw message;
clip from the tail where the failure is; or strip a recognised init/handshake
envelope before clipping.

A 2.8 MB provider message is itself worth a look — whether the whole stream
should be retained as the error payload, or only what follows the handshake.

## Revised acceptance

- a provider-broken attention names why it broke (the error keyword and status)
  without an operator opening the log
- a megabyte provider message still produces a bounded alert (unchanged — this
  already works; a scenario should pin it so it stays true)
- provider, model and session still lead and are never truncated away
- a short provider message is unchanged
