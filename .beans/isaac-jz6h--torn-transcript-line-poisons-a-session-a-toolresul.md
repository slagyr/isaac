---
# isaac-jz6h
title: 'Torn transcript line poisons a session: a toolResult entry was written mid-line into another entry; every retry dies and the delivery dead-letters'
status: draft
type: bug
priority: high
tags:
    - session
    - durability
    - hail
created_at: 2026-09-04T16:52:39Z
updated_at: 2026-09-04T21:47:21Z
---

2026-09-04 16:48:51Z, isaac-work-1 (scrapper on gpt-5.4), during isaac-v1la's work turn (hail 0fd02d06). `current.ednl` got a torn line at byte offset 416664: the tail of a toolResult content string ("…only dots and the su") is immediately followed by `{:type "message", :id "b37497a6", :parentId "5d11a594", … :role "toolResult", :id "fc_7200e397-…"` — a second entry started mid-line. From then on every turn on the session fails at drive/turn.clj:1399 reading the transcript (`NumberFormatException: For input string: "5d11a594"` — the EDN reader is mid-token when it hits the fused line). The delivery retried 5× thirty seconds apart, each appending an `:type "error"` entry, and dead-lettered at 16:51:32Z — a healthy bean lost to a poisoned session ([[hails-never-die]]: dead-letter is for poison in the BEAN, not the session).

Two defects:
1. **Durability: transcript appends are not atomic per line.** Two writers (the turn's mid-loop toolResult persist and something else — the 429 wall retry path / the error-entry append / a concurrent compaction splice?) interleaved inside one line. isaac-vdfc's torn-line repair only covers the file TAIL at resume; a mid-file tear is never repaired. Need: single-writer append with a line-level lock (the transcript lock exists for compaction — extend it to every append), write the whole line in one syscall (build the string, then one `append`), and a fsck at open that quarantines an unparseable line (move it aside, log `:session/transcript-torn :offset`) instead of throwing.
2. **Hail: a session-poison failure must not burn the bean's attempts.** When a turn dies before the model is called with the same exception on consecutive attempts, the delivery worker should mark the SESSION unhealthy (rebind the hail to another session / archive-and-recreate the session as the isaac-work-2 archive path already does) rather than counting toward dead-letter.

Evidence on zanebot: `~/.isaac/sessions/isaac-work-1/current.ednl` (222 lines, 449 KB) — the torn line is the one containing `the su{:type "message"`; `~/.isaac/hail/failed/0fd02d06.edn`. The isaac-foundation-v1la checkout on isaac-work-1's role home holds the worker's progress.

Scenarios (@wip, worker writes): (1) an append that races another writer still yields one entry per line (spec with two threads appending 1000 entries; every line parses); (2) a transcript with one fused line is quarantined at open — the turn runs, `:session/transcript-torn` is logged, the quarantined line is written to `current.ednl.torn`; (3) a delivery whose turn throws the same pre-model exception twice gets the session marked unhealthy and the hail rebinds/defers without consuming an attempt.



## Exhibit 2 — recovery friction (16:51–16:59Z)
- A delivery's `:bound-session` follows a renamed session: after `sessions rename isaac-work-1 isaac-work-1-archive-…` the re-hail (45a0023f) still bound to the archive and dead-lettered there. Renaming/archiving a poisoned session must also invalidate deliveries bound to it (or the worker must re-select when the bound session's transcript fails to open).
- `isaac sessions set <id>.tags '#{:ci :isaac}'` rejects every value form with "must be a set of keywords" (schema.clj:64 validation vs the CLI's string parse) — see the separate bean. Workaround used: `isaac prompt --crew scrapper --tag ci --tag isaac --create always` mints a tagged session (`cheery-rowan`); the band matches on tags, not names.



## Exhibit 3 (2026-09-04 21:18–21:21Z, cheery-rowan)
Second torn transcript in one day, on the session that had just finished isaac-v1la: `Invalid number: 2026-09-04T21, offset: 46148` (a timestamp token fused mid-line). Timing lines up with the 21:18:38 server restart (clean-suspend phase) and/or the 21:19–21:23 window when `/usr/local/bin/isaac` was unlinked by a failed brew upgrade — a tool shell-out failing mid-turn while the drive was persisting. Whichever it was, the append is not atomic and the file is left unparseable. A ci-failure delivery (ed0d19f9) burned 4 attempts on it before I archived the session as `cheery-rowan-torn-20260904` (tags cleared) and minted `snappy-toad` for the band. Add to scenarios: a restart (suspend) that lands mid-append must not leave a partial line — either the append completes or nothing of it is written.



## Exhibit 4 (2026-09-04 21:41Z, isaac-work-2)
Third torn transcript of the day: `Invalid number: 2026-09-04T21, offset: 25032, context: … bean, do not re-qu{:type` — an entry truncated mid-string with the next append fused onto it. isaac-work-2 was mid-turn on the jllj work re-hail when the 21:18:38 `launchctl kickstart -k` restart hit. All three tears today (isaac-work-1 this morning, cheery-rowan, isaac-work-2) coincide with server restarts during in-flight turns: the restart kills the JVM between the truncating write and the newline/rest of the record. The suspend phase must either finish the append or roll it back; the re-open path must at minimum skip a torn trailing record instead of poisoning the whole session. Cost so far today: 3 sessions archived, 2 hails dead-lettered (v1la, jllj re-hail). Sessions archived: isaac-work-2-torn-20260904 (tags cleared); band now on snappy-toad.
