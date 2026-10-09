---
# isaac-xklq
title: 'isaac-handbook: scrub real names from examples — Marigold fixtures only'
status: in-progress
type: task
priority: critical
created_at: 2026-10-09T16:58:58Z
updated_at: 2026-10-09T17:04:41Z
---

URGENT (Micah, 2026-10-09). Amended the same hour: the deployment's crew name is scrubbed too (standing ruling of 2026-09-19). This repo is public and its examples carry a real company name, a real person's name and what look like real Google ids. Replace them with Marigold fixtures. Mechanical; no behavior change.

## Scope

Every tracked file in `isaac-handbook`: features, specs, feature-steps, src docstrings and messages, resources, README, docs.

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



## Acceptance

- `git grep -i tonotop` prints nothing.
- `git grep -i yopp` prints nothing.
- `git grep -i "micah"` prints only decision-attribution lines.
- `git grep -E "users/[0-9]{12,}"` prints only the replacement ids.
- The repo's full verification is green (`bb verify`, and `bb jvm-spec` / features where the repo has them).
- No pin, dependency or behavior change. One commit.

## Likely repo scope

`isaac-handbook`.


## Implementation conflict (2026-10-09)

Scrub requires editing `isaac-handbook/features/reference.feature:124,127,133` (`google.tonotop.oauth.client-secret`), but the gated work protocol permits only removing `@wip` from any `.feature` file, regardless of whether a bean has a feature baseline. No product edits made. Also `isaac-handbook/LICENSE:3` contains `Copyright (c) 2026 Micah Martin`: acceptance says `git grep -i micah` prints only decision attributions; changing a copyright attribution to a fictional person is not a mechanical fixture scrub and needs an authorized legal decision. Please clarify the feature-edit exception and whether LICENSE should be excluded from acceptance or updated by its owner.

## Ungated

No feature runner contract to baseline. The features are the examples being scrubbed, not a frozen behavior contract. `bb bean-gate verify` will exit 2. That is expected.

## Planner note (2026-10-09)

The `@wip`-only rule applies to a **baselined** bean. This one has no `feature-baseline:`. Scrub identifiers in the feature files the acceptance names, including `features/reference.feature`. Do not add, delete, or reword a scenario. Close ungated: `beans update isaac-xklq --tag=unverified`, stay `in-progress`, hail `isaac-verify`. Do not land it yourself and do not mark it `completed`.

`LICENSE` is a copyright notice, not an example. Leave it. The micah grep may also print that one copyright line. Everything else named Micah is either a decision attribution or gets scrubbed.
