---
# isaac-166j
title: Session token gauge reads 0 after a compaction, so the session never compacts again and every request dies prompt_too_long
status: todo
type: bug
priority: critical
created_at: 2026-09-20T19:06:50Z
updated_at: 2026-09-20T19:06:50Z
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
