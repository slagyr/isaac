---
# isaac-qqsn
title: 'Transcript reader races a large append: EOF while reading string on a file that is intact a minute later'
status: scrapped
type: feature
priority: high
created_at: 2026-09-06T18:36:19Z
updated_at: 2026-09-06T19:01:29Z
---

Repo: isaac-agent (session store read path, `impl_common.clj` read-session-entry / transcript readers). Follow-up to isaac-jz6h (append lock under parallel tool batches, shipped in agent 0.1.46).

## Evidence (2026-09-06 18:33:13Z, tono-work-1, agent 0.1.47)
Hail 567b453a (tono-2fe1) attempt 1 failed with `java.lang.RuntimeException: EOF while reading string: \"690: … (defui footer-dialog …` — a tool-result record (a large file read) cut mid-string. Two minutes later the same `current.ednl` is intact (142 lines, 444 KB, last record 18:35:40, final line well-formed) and the session kept working. No restart in the window (last boot 18:10:48). So the writer was fine: the READER opened the file while the large append was in flight and parsed a partial trailing record. The jz6h append lock serialises writers; readers do not take it.

## Required
- A transcript read must never observe a partial trailing record: either take the session's append lock for the read, or read only up to the last newline-terminated record and treat trailing bytes as in-flight (not an error).
- Scenario (isaac-agent, @wip, features/session/storage.feature or a new transcript_durability.feature): a reader that opens the transcript while a large record is being appended sees the complete previous records and not a parse error; the appended record is visible on the next read.
- The delivery worker must not burn an attempt on a transient read race: retry the read once before failing the turn.

Cost: 1 hail attempt. Different from jz6h exhibits 1–6 (those files were torn on disk); this file was never torn.



## Reasons for Scrapping (2026-09-06)
Already fixed by isaac-4zr3 (completed 2026-09-05, Micah's decision: one persist lock per session taken by every persist AND every read; temp+rename for whole-file writes). On agent main `impl_common.clj` `read-ednl` now runs under `with-persist-lock`, which closes exactly this race. The 18:33Z failure happened on agent 0.1.47; 4zr3 shipped to zanebot in 0.1.48 at 18:40Z. Reopen only if an `EOF while reading string` turn failure recurs on 0.1.48+ with an intact file.
