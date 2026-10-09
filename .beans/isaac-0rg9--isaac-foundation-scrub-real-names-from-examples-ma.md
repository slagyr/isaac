---
# isaac-0rg9
title: 'isaac-foundation: scrub real names from examples — Marigold fixtures only'
status: in-progress
type: task
priority: critical
tags:
    - unverified
created_at: 2026-10-09T16:58:58Z
updated_at: 2026-10-09T17:17:04Z
---

URGENT (Micah, 2026-10-09). Amended the same hour: the deployment's crew name is scrubbed too (standing ruling of 2026-09-19). This repo is public and its examples carry a real company name, a real person's name and what look like real Google ids. Replace them with Marigold fixtures. Mechanical; no behavior change.

## Scope

Every tracked file in `isaac-foundation`: features, specs, feature-steps, src docstrings and messages, resources, README, docs.

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

`isaac-foundation`.

## Worker conflict (2026-10-09)

Scope requires changing tracked `.feature` prose: `features/cli/config_default_labels.feature:5` contains “yopp”. The hail-bean-work-gate worker rule allows only removing `@wip` in any `.feature` file. This bean has no feature-baseline and no `@wip` to remove. Planner must reconcile the feature-file ownership rule with this required scrub (for example, edit that feature on module main or explicitly authorize the mechanical change). No implementation files have been edited. Gate has not been run yet.

## Ungated

No feature runner contract to baseline. The features are the examples being scrubbed, not a frozen behavior contract. `bb bean-gate verify` will exit 2. That is expected.

## Planner note (2026-10-09)

The `@wip`-only rule applies to a **baselined** bean. This one has no `feature-baseline:`. Scrub identifiers in the feature files the acceptance names, including `features/cli/config_default_labels.feature:5`. Do not add, delete, or reword a scenario. Close ungated: `beans update isaac-0rg9 --tag=unverified`, stay `in-progress`, hail `isaac-verify`. Do not land it yourself and do not mark it `completed`.

`LICENSE` is a copyright notice, not an example. Leave it. The micah grep may also print that one copyright line. Everything else named Micah is either a decision attribution or gets scrubbed.

## Worker handoff (2026-10-09)

branch: bean/isaac-0rg9 @ 1f66e3222190ffc809cf98be828db411b69d5b9d (base origin/main@edc18481808a050f8c2fd657f2e966e6040fcfc3)

Mechanical fixture-only replacement in three files: yopp → isaac (feature prose and spec label), tonotop.com → marigold.example (spec sample addresses). LICENSE and decision attributions unchanged; no scenarios added/deleted/reworded. No pin, dependency, or behavior change. `git grep -i tonotop` and `git grep -i yopp` empty; `git grep -i micah` only decision attributions and LICENSE; no long numeric users ids.

Validation: `ISAAC_TEST_TIMEOUT_MS=240000 bb ci` passed (1348 specs; 361 features, 2 pre-existing pending). Default `bb ci` timed out during features at 60s; rerun with expanded timeout passed. `bb jvm-spec` fails 9 unrelated existing tests (gitlibs directory/environment assumptions and lifecycle exception); `bb jvm-features` fails 1 unrelated gitlibs cache directory expectation in `cli/modules_pins.feature:143`. `bb bean-gate verify isaac-0rg9` exits 2 (ungated, as planned).
