---
# isaac-lru8
title: A transcript line that isn't valid EDN fails every reader of the whole transcript
status: draft
type: bug
priority: normal
created_at: 2026-11-16T23:53:14Z
updated_at: 2026-11-16T23:53:14Z
---

Found 2026-11-16 through the dream habits-section failures (37 of them were 'Invalid number: 1-2.' on scrapper turns).

zanebot `sessions/scrapper/isaac-work-1/30.ednl` (written 2026-08-22), lines 137 and 141, are not valid EDN. A tool-call argument containing `\\"` (a shell `git log` with `Isaac-Session:` / `Isaac-Bean:` trailers) was written without escaping, so the reader sees keyword soup: `… :unchanged in claude_cli.feature scenarios 1-2. "", :Isaac-Session: isaac-work-1 …`.

Two questions:
1. Writer: does the current transcript writer still produce this? It's 3 months old; check whether the writer since moved to pr-str for every value. Write a spec with a tool argument holding `\\"`, newlines and `\\\\`, and round-trip it.
2. Reader: one bad line throws for the whole transcript (`chronicle-transcript`). It should skip the line, log `:transcript/line-unreadable` with session, file, line number, and keep going.

dream 0.4.4 stopped the turn path from reading transcripts, so this no longer drops habits; it still affects anything that reads that session (recall, sessions show, a dream run for scrapper if it ever dreams).

Needs scenarios before dispatch.
