---
# isaac-6yg0
title: 'ACP surface bypasses the episode router: session/new + session/prompt must dispatch through bridge for :episodes crews'
status: todo
type: bug
priority: high
created_at: 2026-08-29T05:16:30Z
updated_at: 2026-08-29T05:37:39Z
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



## Correction + findings (2026-08-28, plan)

- `:conversation` **is** in the crew schema (isaac-agent manifest, crew
  entry) — an earlier note here blaming a missing schema key was wrong.
- Deployed isaac-acp (176339e) **does** call `bridge/dispatch!` from
  `run-prompt`; the router still did not fire on zanebot (no
  `:episodes/opened` for marvin via ACP; the CLI `prompt` path on the same
  crew did open one). The two ACP-specific differences are the leads:
  1. `session-prompt-handler` builds `effective-cfg` via
     `config/normalize-config` (lexicon conform per crew; any conform error
     collapses that crew to `{}`) and passes it as the charge `:config`
     instead of the loader snapshot every other surface uses.
  2. `session-new-handler` pre-creates the session directly via
     `session.context/create-with-resolved-behavior!`.
  Root cause is to be PROVEN by the worker with a failing scenario, not
  assumed — the fix is the same either way (below).
- **isaac-acp pins isaac-agent at 44b55e3 — 188 commits behind main, before
  episodes existed.** Its suite cannot run these scenarios until the pins
  (agent, agent-spec, server, foundation) are bumped to current. That bump
  is step 1 of this bean and may surface unrelated drift; land it as its own
  commit.
- Audit of the other surfaces (same session): cron → `bridge/dispatch!` ✓;
  discord/imessage → comm delivery → bridge ✓; **hail delivery worker calls
  `turn/run-turn!` directly** (delivery_worker.clj:351) after pre-creating
  sessions — a second bypass. Worker crews are chronicle today so it is
  latent; file it as a sibling bean when this one lands (do not widen scope
  here).

## Scenarios (committed @wip at slagyr/isaac-acp 6bc9b27)

features/comm/acp/episodes.feature — 4 scenarios:

- :29 session/prompt on an episodes crew → `:episodes/opened` with
  `:thread` = the ACP sessionId and `:origin acp`; backing session id is an
  episode id.
- :54 warm second prompt appends — no `:episodes/closed`.
- :87 chronicle crew unchanged — named session created, transcript stored,
  no `:episodes/*` events (regression net alongside the existing ACP
  features).
- :117 `acp --crew <episode-crew>` attaches to a fresh thread and replays no
  chronicle transcript.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / the ACP commands are registered / the ACP client has initialized / the isaac EDN file … exists with / config file … containing / the current time is (Z form) / the following model responses are queued / the ACP client sends request / the ACP agent sends response / the log has entries matching / the log has no entries matching / the following sessions match / session … has transcript(:|matching) / the following sessions exist (updated-at) / stdin is / isaac is run with / the stdout has a JSON-RPC response / the stdout does not contain / the exit code is | reuse |

No new steps. `:origin` on `:episodes/opened` is a log-field addition in
isaac-agent (design item 4) — a one-line change; if it needs its own pin
bump, drop the `origin` column and note it here.

## Acceptance

In slagyr/isaac-acp:

1. Bump deps.edn + bb.edn pins (isaac-agent, isaac-agent-spec, isaac-server,
   isaac-foundation + spec-support) to current main; `bb spec` and
   `bb features` green on the bumped pins BEFORE touching ACP code.
2. Remove `@wip` from features/comm/acp/episodes.feature, then:

       bb features features/comm/acp/episodes.feature
       bb features features/comm/acp
       bb spec

3. Field check on zanebot after deploy (record here): `toad acp "zane-isaac
   acp --crew marvin --create always"`, one prompt → `isaac episodes list
   --crew marvin` shows an open episode on the ACP session id; recall block
   present when the query matches the corpus.
