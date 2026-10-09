---
# isaac-izrn
title: 'isaac-gchat: scrub real names from examples — Marigold fixtures only'
status: in-progress
type: task
priority: critical
tags:
    - unverified
created_at: 2026-10-09T16:58:58Z
updated_at: 2026-10-09T17:07:45Z
---

URGENT (Micah, 2026-10-09). Amended the same hour: the deployment's crew name is scrubbed too (standing ruling of 2026-09-19). This repo is public and its examples carry a real company name, a real person's name and what look like real Google ids. Replace them with Marigold fixtures. Mechanical; no behavior change.

## Scope

Every tracked file in `isaac-gchat`: features, specs, feature-steps, src docstrings and messages, resources, README, docs.

## Replacements — use exactly these, so the repos stay consistent

| Find (case-insensitive) | Replace with |
|---|---|
| the domain `tonotop.com` | `marigold.example` |
| `tonotop` anywhere else (organization id, project ids, session names, config paths such as `google.tonotop.…`) | `marigold` |
| `Micah Martin` | `Hieronymus Finch` |
| `micah@…`, `micah-martin`, bare `micah` used as a sample sender, user or query | `hieronymus@…`, `hieronymus-finch`, `hieronymus` |
| `yopp` anywhere (the account `yopp@…`, `users/yopp`, project and session names such as `…-yopp`, prose) | `isaac` (`isaac@marigold.example`, `users/isaac`, `marigold-isaac`) |
| any long numeric `users/<digits>` id | `users/100000000000000000001` (keep distinct ids distinct: …002, …003) |
| any `domainId` / customer id that is not obviously fake | `0marigold` |

Leave alone: decision attributions in comments and feature headers of the form "Decision (date, Micah)" or "(Micah, date)" — those are not examples and are a separate ruling.

The README session-name examples and the DM session names in features change together.

## Acceptance

- `git grep -i tonotop` prints nothing.
- `git grep -i yopp` prints nothing.
- `git grep -i "micah"` prints only decision-attribution lines.
- `git grep -E "users/[0-9]{12,}"` prints only the replacement ids.
- The repo's full verification is green (`bb verify`, and `bb jvm-spec` / features where the repo has them).
- No pin, dependency or behavior change. One commit.

## Likely repo scope

`isaac-gchat`.

## Conflict (2026-10-09)

The acceptance requires scrubbing `features/comm/gchat/*.feature` (numerous real company/person identifiers), but the worker's `hail-bean-work-gate` contract states: "Removing `@wip` is the only edit you make to any `.feature` file." This bean has no `feature-baseline:` and is a fixture-scrub task, not a behavior change; nevertheless its required feature edits violate the explicit worker restriction. No implementation files edited. Planner must clarify/adjust the work contract for this bean before implementation can proceed.

## Ungated

No feature runner contract to baseline. The module's features are the examples being scrubbed, not a frozen behavior contract. `bb bean-gate verify` will exit 2. That is expected.

## Planner note (2026-10-09)

The `@wip`-only rule applies to a **baselined** bean. This one has no `feature-baseline:`. Take the ungated close: do the scrub, including every `.feature` file the acceptance names, then `beans update isaac-izrn --tag=unverified` while it stays `in-progress`, and hail `isaac-verify`. Do not land it yourself and do not mark it `completed`.

Identifier swaps inside an existing step are in scope. Do not add, delete, or reword a scenario. A step that matched `tonotop` must match `marigold` after the swap, and nothing else about the scenario changes.


## Implementation (2026-10-09)

branch: bean/isaac-izrn @ 4f46520 (base origin/main@75e4e95) in isaac-gchat. Mechanical identifier replacements across 28 tracked files, including four feature files; no scenario names/counts changed. Long user IDs map independently to users/100000000000000000001 and users/100000000000000000002; domainId 0ivzlyj to 0marigold. No dependencies or pins edited. `git grep -i` for tonotop/yopp/micah is empty; long users IDs only replacements. `bb ci` green (208 spec examples, 71 feature examples); `bb jvm-spec` green (208 examples). `bb bean-gate verify isaac-izrn` exits 2 (no baseline). This repo has no `bb verify` task; `bb ci` is its full verification task.
