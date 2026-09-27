---
# isaac-vp7h
title: Session selection merges default frequencies and drops prompt-default
status: draft
type: bug
priority: high
created_at: 2026-09-27T01:00:25Z
updated_at: 2026-09-27T01:00:25Z
---

Repo: **isaac-agent**, plus every turn consumer that builds a frequencies map (prompt, acp, discord, gchat, hooks, cron, hail).

## Where prompt-default came from

isaac-4e4b (2026-06-26) unified session selection and left one case open: what a turn targets when no filter is given. The note said keep the named session `prompt-default` as the predictable default, and settle it later. It was never settled. `resolve-session-targets` and `build-frequencies` still hardcode `:default-session-key "prompt-default"`. `isaac prompt` with no session name takes that path, so the session id is the literal string `prompt-default`.

`:defaults :frequencies` is already the config for that choice (`isaac.config.defaults/frequencies-template`). Nothing merges it in. On Yopp the map is `{:crew :yopp}`, which would have selected a yopp session. The prompt command used it only as the crew, and kept `prompt-default` as the session. `prompt_cli/resolve-target` also special-cases an explicit `--crew` into `policy/default-session` and skips the frequencies resolver.

## Decision (2026-09-27, Micah)

`prompt-default` goes away. The prompt command is not a special case. Any turn that identifies a session merges `:defaults :frequencies` underneath the frequencies the consumer supplied. Consumer keys win. One merge, used by every consumer, not a copy in each surface.

A consumer that needs a stable id of its own (a Discord channel's `discord-<id>`, a hook's `hook:<name>`, a gchat space) puts that in its frequencies, and it wins over the defaults.

If the merged map still names no session, crew, or tags, the turn fails. It does not invent a session id.

## Call sites that build frequencies today

- `isaac.session.frequencies/resolve-session-targets` — hardcoded `prompt-default`
- `isaac.session.frequencies-cli/build-frequencies` — same default
- `isaac.bridge.prompt-cli/resolve-target` — `--crew` bypasses the resolver
- `isaac.comm.acp.cli` — `build-frequencies`
- `isaac.comm.discord/channel->frequencies` — `discord-<channel-id>` when the channel names nothing
- `isaac.comm.gchat.handler` — its own `default-session-key`
- `isaac.hooks/build-frequencies-from-hook` — `hook:<name>` when the hook names nothing
- `isaac.cron.service/job->frequencies` — merges `:create :always`, not the config defaults
- `isaac.hail.router` — merges band and hail frequencies, not the config defaults

## Scenarios still to write

Prove the merge once in the resolver, and one wiring scenario per consumer: a bare prompt with `:defaults :frequencies {:crew "cordelia"}` opens or resumes a cordelia session and never creates `prompt-default`. A consumer-supplied session id still wins. An empty merge fails the turn.
