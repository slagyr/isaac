---
# isaac-9af8
title: 'Provider-broken attention posts the provider''s whole stream: 2.8 MB to Discord'
status: todo
type: bug
priority: high
tags:
    - ops
    - comm
created_at: 2026-09-21T04:46:42Z
updated_at: 2026-09-21T04:46:42Z
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
