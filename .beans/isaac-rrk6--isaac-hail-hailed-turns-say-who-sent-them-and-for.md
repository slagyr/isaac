---
# isaac-rrk6
title: 'isaac-hail: hailed turns say who sent them and for whom (:from and :for)'
status: completed
type: feature
priority: normal
created_at: 2026-10-12T17:49:25Z
updated_at: 2026-10-12T20:27:56Z
parent: isaac-zt1x
---

Part of the contacts epic (isaac-zt1x). Scenarios approved by Micah 2026-10-12. Builds on turn attribution (isaac-v403) and inherited ownership (isaac-4615), both landed.

## What this is

Every turn records `:from`, who started it, and `:for`, the outside party it is done for. A hail is one more way to start a turn, but the turns Hail queues do not say who sent them: they come out as `{:kind :submit}`. This bean makes a hailed turn carry both fields.

## Rules

- **A crew hailing from inside a turn** (`hail-send` tool): `:from {:kind :crew :id <crew>}`, and `:for` is the running turn's `:for`, copied unchanged. Absent stays absent. The agent already does this for any turn submitted while a crew's tool runs (isaac-4615); Hail only has to pin an agent that has it.
- **A hail over HTTP** (`POST /hail/send`): `:from {:kind :http :id "<principal name>"}`. No `:for`.
- **The plain server token** has no named principal. It is the same as the built-in `admin` principal: `:from {:kind :http :id "admin"}` (Micah, 2026-10-12: "admin is fine").
- **A hail from the command line** (`isaac hail send`): `:from {:kind :cli}`. No `:for`.
- An HTTP or CLI caller cannot name who a hail is for. Not wanted now (Micah, 2026-10-12).

## Work

1. Bump the `isaac-agent` and `isaac-agent-spec` pins in `deps.edn` and `bb.edn` to current agent main (at least `49e2cfe`, which has isaac-4615 and the `via memory comm as handle` step). Bump foundation/http pins too if the agent needs them; keep pins coherent.
2. `isaac.hail.queue/send!` passes a `:from` party to `isaac.agent.turn.submit/submit!`. `isaac.hail.http` builds it from the request principal; `isaac.hail.cli` passes `{:kind :cli}`. The tool path passes nothing: the agent stamps crew and owner itself.
3. Remove the five `@wip` tags in `features/attribution.feature` and make them pass. No new steps are needed.
4. Add a short "who sent it" note to Hail's handbook chapter.

## Not in this bean

- The legacy `origin.from` value (`:crew/<id>`, `:http`, `:cli`) and the "From crew:" preamble line stay as they are. They now duplicate `:from`; a follow-up bean removes them.
- Do not change the existing frozen scenarios that assert `origin.from`.

## Acceptance

- `features/attribution.feature`: all five scenarios pass with no `@wip`.
- `bb ci` green in isaac-hail; CI green on main.

feature-baseline: isaac-hail 5ddb82e158c5cc4d4c2fb20ae349b06c105c921c
feature-blob: isaac-hail features/attribution.feature 671f7f267dbe6ea60a3ee688fc7495eddc8baa1c

## Landed on main (2026-10-12)

main-sha: isaac-hail 6614161018e00d661d69defb1c47e479bd558b7b
