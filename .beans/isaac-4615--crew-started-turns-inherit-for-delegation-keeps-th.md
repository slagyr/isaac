---
# isaac-4615
title: Crew-started turns inherit :for — delegation keeps the original owner
status: draft
type: feature
created_at: 2026-10-08T22:03:41Z
updated_at: 2026-10-08T22:03:41Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

DRAFT. Needs scenarios before it is todo. Part of the contacts epic (isaac-zt1x). Blocked by the attribution fields bean (isaac-v403).

## Idea

When a crew member starts a turn from inside a turn, the work still belongs to whoever the first turn was for. Without this, delegation launders ownership: a person asks one crew member, it hails another, and the second turn looks like it belongs to an agent.

## Rule

A turn submitted from inside a running turn gets:

- `:from` = the crew member running the current turn, `{:kind :crew :id …}`;
- `:for` = the current turn's `:for`, copied unchanged. Absent stays absent.

A submitter may not override this from inside a turn: a crew tool cannot claim to act for someone else.

## What it needs

- The running turn makes its `:for` and its crew available to tools (tool context).
- `submit!` applies the rule when called from inside a turn.
- `isaac-hail`: `hail__send` stops writing `origin.from` as `:crew/<id>` and lets the rule fill the top-level `:from`; the hail queue carries `:from` and `:for` through to the turn it creates. Clean cutover, no alias. The HTTP and CLI entry points set `{:kind :http :id <principal>}` and `{:kind :cli}`.
- Foreman `:turn` actions and any other in-turn submitter follow the same rule.

## To settle

- Cron: a job's turns are `{:kind :cron :id <job>}` with no `:for`, unless the job config names one. Whether to allow that is a cron bean.
- How much plumbing the hail queue and delivery path need. Not yet measured.

## Scenarios to draft

- Agent: a fixture tool submits a turn from inside a turn that is for Cordelia; the new record is from the crew member and for Cordelia.
- Agent: the same from a turn that is for nobody; the new record has no `:for`.
- Hail: `hail__send` from a turn for Cordelia produces a turn for Cordelia on the receiving crew.

## Likely repo scope

`isaac-agent`, then `isaac-hail`. Probably two beans once scenarios exist.
