---
# isaac-6yg0
title: 'ACP surface bypasses the episode router: session/new + session/prompt must dispatch through bridge for :episodes crews'
status: draft
type: bug
priority: high
created_at: 2026-08-29T05:16:30Z
updated_at: 2026-08-29T05:20:58Z
---

Repo: **isaac-acp** (`src/isaac/comm/acp/server.clj`, `cli.clj`), possibly a
small seam in **isaac-agent** `bridge/core`. Found 2026-08-28 in the first
real marvin episodes trial (toad → `zane-isaac acp --crew marvin --create always`).

## Problem

isaac-51xy decision 27 says the ACP session-id is a THREAD for episode crews.
But isaac-acp never reaches the router:

- `session-new-handler` calls `isaac.session.context/create-with-resolved-behavior!`
  directly → a plain chronicle session (`sincere-tapir`, crew marvin) even
  though marvin is `:conversation :episodes`.
- `session-prompt-handler` → `run-prompt` with the raw sessionId; the episode
  router lives in `bridge/core` `ensure-session!` (isaac-qxvl), which this
  path skips. Result: no `:episodes/opened`, no recall-at-open, no sealing.
- `cli.clj` attach (`--crew` → most recent session → `attach-session-result!`
  replays that chronicle's transcript into the client). For an episode crew
  that replays the wrong container (the old chronicle), and the attached key
  becomes the thread — in the field this was the **#general Discord channel**
  session, i.e. toad and Discord would have shared an episode chain.

Observed cost: without recall, marvin answered "what joke did you text my
dad?" by `memory__search` + globbing/grepping `~/.isaac` + reading episode
`.md` files by hand (8 tool calls) — doing recall's job manually, expensively,
and only because `fs/*` happens to be allowed.

## Design

1. `session/new` for an `:episodes` crew creates a THREAD key (the ACP
   sessionId) and nothing else; the first `session/prompt` goes through
   `bridge/core` dispatch so the router opens the episode (recall-at-open
   fires). Chronicle crews unchanged.
2. `session/prompt` always dispatches via the bridge entry (same seam as
   comm/hail/CLI), not a private `run-prompt`.
3. `session/load` and the `--crew` attach for episode crews: replay the
   thread's OPEN episode transcript if any (that is the live conversation),
   never a chronicle; with no open episode, replay nothing (recall will
   supply context on the first prompt). `--crew` with no explicit session
   should default to a fresh thread for episode crews rather than "most
   recent session".
4. Log `:episodes/opened` with `:origin :acp` so the field check is visible.

## Scenarios (to write before todo — features/acp/episodes.feature)

- `:conversation :episodes` crew, ACP `session/new` + `session/prompt` →
  one open episode with `:thread` = the ACP sessionId; transcript has the
  recall event when the corpus matches.
- second `session/prompt` within TTL → same episode (warm append).
- chronicle crew → behavior byte-identical to today (existing ACP features
  are the regression net).
- `--crew <episode-crew>` attach with no open episode → no transcript replay.

## Workaround (today)

Drive episode crews through a routed surface: Discord, hail, or
`isaac prompt --crew marvin --session <thread>` (prompt_cli has its own
`ensure-session!` that routes).



## Ruling (2026-08-28, Micah)

**ACP must dispatch through the bridge, always.** No private `run-prompt`,
no direct `create-with-resolved-behavior!`. Every surface (comm, hail, cron,
CLI, ACP) enters at the one bridge seam — that is where the episode router,
turnstiles, observers and finalization live, and a surface that skips it
silently loses all of them. Design item 2 above is the whole bean; items 1,
3, 4 follow from it. Audit the other surfaces (isaac-cli-server, cron,
imessage/discord adapters) for the same bypass while here and note findings.

Trial fallout struck from marvin's history 2026-08-28: chronicle session
`sincere-tapir` (ACP) and episode/session `2026-08-29-0516-ncl8` (cli-1)
deleted; neither was indexed.
