---
# isaac-9af8
title: 'Provider-broken attention posts the provider''s whole stream: 2.8 MB to Discord'
status: in-progress
type: bug
priority: high
tags:
    - ops
    - comm
    - unverified
created_at: 2026-09-21T04:46:42Z
updated_at: 2026-09-21T22:23:39Z
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

## Work log (2026-09-21, work-2 local)

Landed as isaac-agent d016cd3, suites green (1696 specs / 0, 845
features / 0).

## Premise correction (verified against zanebot)

The deployed build at 04:31Z (isaac.agent 53a1f0e) **already carried the
fc30085 enqueue clip** — its gitlib attention.clj has content-cap 1000.
The quoted post ending "… truncated 2844251 bytes" is that clip's
dropped-count notice: ~1KB was enqueued, not 2.8MB. The 44-second
queue→delivered gap for delivery 0772 (04:31:24 queued, 04:32:08
delivered) is the comm delivery worker's tick cadence, not upload time.
So "2.8 MB to Discord" overstates what left the machine — but the
acceptance gaps below were real on main.

## What actually changed

- `provider-content` bounds the raw provider message itself
  (`provider-message-cap 400`, named constant): provider/model/session
  always lead and are never squeezed; the message keeps a head plus
  "… N characters dropped". Previously the first ~940 chars of a dump
  rode along inside the 1000-char blob clip.
- Wording: the clip counts characters and now says so ("bytes" was
  wrong — `count` of a string).
- The full untruncated text reaches the log:
  `:attention/provider-message-clipped` (message-chars + full-message)
  when the message cap fires; `:attention/content-clipped`
  (content-chars + full-content) when the enqueue-level backstop fires.
- The bean's open question answered: `enqueue-attention!` keeps capping
  **all** attention content as the backstop — any future caller is
  bounded, and now also logged.

Specs (all red before the change): message-level bound with leaders
leading; short message untouched (no ellipsis, no dropped notice);
full-message log entry; enqueue-level backstop clip + full-content log
via a non-provider caller (turn-failed). The fc30085 spec's "truncated"
wording expectation updated to "characters dropped".



## Verification failed

HEAD: isaac-agent d016cd37d0b01e1504b802c91f7a106ee93df187 (on origin/main)
Working tree: clean
Beans HEAD at review: 7971cbb8
No Exceptions section on this bean. No feature file in the diff.

`bb spec` 1696 examples, 0 failures. The cap work holds: `provider-message-cap` is 400, leaders stay in front, a short message is unchanged, a giant message says how many characters were dropped, and `:attention/provider-message-clipped` keeps the full text. No `Thread/sleep` in `spec/`.

Revised acceptance, first bullet, is not met. A provider-broken attention still does not name why it broke. `maybe-notify-broken!` forwards only `:message`. `provider-content` clips the head of that string. On the 04:31 incident the head is the Claude `system/init` handshake, so the alert is still the tool list, and `:error` / `:status` (logged beside it in `dispatch.clj`) never reach the post. Opening the log is still required. The revised work named three ways to carry the diagnosis: the structured error and status, a tail clip, or stripping the handshake before clipping. A shorter head is not one of them.

## Work log — round 2 (2026-09-21, work-2 local)

Landed as isaac-agent f83af55, suites green (1698 specs / 0, 846
features / 0).

Round-1 verify failure addressed point by point:

- **The diagnosis reaches the post.** `maybe-notify-broken!` now
  forwards `:error` and `:status` from the result, and `provider-content`
  leads with them: "Provider claude is broken error llm-error status
  nil model … session …". An operator reads why without opening the log.
- **The clip is now a tail clip.** A streamed provider error's head is
  the system/init handshake; the failure is at the end. The alert keeps
  the tail (provider-message-cap) plus "… N characters dropped". Short
  messages unchanged; the full text still goes to the log
  (:attention/provider-message-clipped) when the cap fires.
- **Handshake-stripping weighed and rejected** — the third option: it
  means parsing a provider-specific envelope shape, brittle the moment a
  provider reorders its init event. The structured fields plus the tail
  carry the diagnosis without it.
- **The bounded alert is pinned by a scenario**, as the revised
  acceptance asked: features/llm/provider_attention.feature now has a
  giant streamed http-error scenario asserting the outbox record contains
  "error api-error", "status 400", the tail fragment, and the dropped
  count — verified red on round 1's code (stash-checked) before landing.
- The whole-stream-as-payload question: the full stream stays in the
  log; the alert never carries it.
