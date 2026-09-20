---
# isaac-166j
title: Session token gauge reads 0 after a compaction, so the session never compacts again and every request dies prompt_too_long
status: todo
type: bug
priority: critical
created_at: 2026-09-20T19:06:50Z
updated_at: 2026-09-20T19:52:55Z
blocking:
    - isaac-ebup
---

`isaac sessions list` on zanebot, 2026-09-20 19:05Z:

| session | file | gauge | window | % |
| --- | --- | --- | --- | --- |
| isaac-work-1 | 341K | 124,478 | 200,000 | 62% |
| isaac-work-2 | 2.5M | **0** | 200,000 | **0%** |
| isaac-work-3 | 2.1M | **0** | 200,000 | **0%** |

The zeros are wrong. Summing `:tokens` off the entries on disk gives **590,360**
for work-2 (235 entries) and **505,289** for work-3 (125 entries) — 3x the
window, and the counts are right there in the transcript. Each of those two
sessions contains exactly one `compaction` entry; work-1, whose gauge is fine,
had none at the time.

The consequence is not cosmetic. A gauge of 0 never crosses the compaction
threshold, so the session never compacts again and grows without limit. Every
hail bound to it then dies. From the claude CLI, verbatim:

    terminal_reason: "prompt_too_long"
    api_error_status: 400
    result: "Prompt is too long · the request is ~1262766 tokens (limit 1000000)
    but this conversation is only ~596414 tokens — the rest is system prompt,
    tool definitions, and attachment content. A single-exchange conversation
    cannot be compacted; reduce attached files"

Five hails dead-lettered this way between 18:45 and 18:56 — isaac-01vv,
isaac-yxch, isaac-2y86, isaac-e20m, isaac-sgem — plus isaac-srz1, which bounced
off work-2 and went back to `todo`. All of them logged as a generic
`:llm-error`; nothing in the hail record says "that session is too big to
answer", so the band keeps routing work into two sessions that cannot answer
anything.

Work:

- Find why the gauge reads 0 after a compaction (it is the reading, not the
  data — the token counts are on the entries). Suspect the walk from the
  compaction marker: a parent chain that does not resolve, or a sum that starts
  at the marker and stops immediately.
- Make the gauge's failure mode loud: a session whose transcript is megabytes
  and whose gauge is 0 should warn, not read 0%.
- `prompt_too_long` / `api_error_status: 400` is a poisoned-session signal, not
  weather and not a generic llm-error. Classify it, say so in the hail record,
  and take the session out of band rotation instead of feeding it four more
  attempts (see isaac-nceb for the general shape).

Scenarios: a session with a compaction marker and 500K of tokens on its entries
reports its real size; a turn whose request exceeds the provider's limit ends
with a named error that identifies the session as the problem; a hail bound to
such a session is not retried into it.

Immediate workaround on zanebot: archive `isaac-work-2` and `isaac-work-3`
transcripts (`current.ednl` aside, as `isaac-work-2-archive-20260903` already
shows) so the band has three sessions that can answer.

## Mechanism (read the code and the records, 2026-09-20 19:20Z)

My first note guessed at "the walk from the compaction marker". Wrong guess;
here is what it actually is.

`isaac.session.compaction/context-gauge` does not measure the conversation. It
computes:

    last-input-tokens + last-output-tokens
      + stamped :tokens of entries appended after :tally-after-id
      + pending input

That is "what the provider said the last request cost, plus what has arrived
since" — a running tally, not a sum of the transcript. It is the right idea
(the provider's own count beats an estimate) and it has one sharp edge: it
trusts the last request's *reported* usage.

The session records on zanebot:

| session | last-input-tokens | last-output-tokens | tally marker | gauge |
| --- | --- | --- | --- | --- |
| isaac-work-1 | 200000 | (set) | present in transcript | 124,478 |
| isaac-work-2 | **0** | **0** | present, at the tail | **0** |
| isaac-work-3 | **0** | **0** | present, at the tail | **0** |

And a failed oversized request reports exactly those zeros — from the CLI
payload that killed these sessions:

    "usage":{"input_tokens":0,"output_tokens":0,...},"duration_api_ms":0,
    "terminal_reason":"prompt_too_long","api_error_status":400

So the loop closes on itself:

1. the session grows past the window;
2. the next request is refused as too long, and the refusal carries usage zeros
   because no inference happened;
3. Isaac stamps last-input 0 / last-output 0 and advances the tally marker to
   the tail;
4. the gauge now reads ~0 — "this session is 0% full" — so compaction never
   fires;
5. the next hail builds the same oversized request. Forever.

**The failure erases the evidence of its own cause.** Nothing is
miscounted in the transcript; the entries carry 590,360 and 505,289 tokens and
are correct. The gauge is a faithful record of a request that never ran, being
used as a proxy for how much conversation exists.

Work, sharpened:

- A response that carries an error, or zero input tokens, must not overwrite
  the tally. Keep the previous reading, or fall back to summing the transcript,
  but never let a failed request reset the gauge to zero.
- `should-compact?` should have a floor that does not depend on the last
  response at all — transcript bytes or stamped-token sum — so a session can
  always be rescued by the thing designed to rescue it.
- `prompt_too_long` (`api_error_status: 400`, `terminal_reason`) is a
  poisoned-session signal: name it, take the session out of band rotation, and
  do not spend four more attempts on it (see isaac-nceb).

## Deployed, and what it did and did not fix (2026-09-20 19:50Z)

Agent 0.1.73 (`d0eed3d`) is live on zanebot. The gauge now reads true —
isaac-work-2 went from `0 / 0%` to `590,360 / 295%` — and a probe hail proved
the decision half works: compaction **started** for the first time, where
before it was never attempted.

It could not finish. The session's largest entry is a single 511,568-token
assistant message, so the chunk plan came back `:oversized-single`, compaction
failed, and the turn ended `:context-exhausted` — though the hail **deferred**
rather than dead-lettering, which is the weather path behaving.

So this bean's fix stands and is necessary, but it is not sufficient: see
isaac-ebup for the entry that cannot be chunked, which is what actually killed
isaac-work-2 and isaac-work-3.
