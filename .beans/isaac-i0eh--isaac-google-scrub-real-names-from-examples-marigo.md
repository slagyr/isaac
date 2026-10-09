---
# isaac-i0eh
title: 'isaac-google: scrub real names from examples — Marigold fixtures only'
status: completed
type: task
priority: critical
created_at: 2026-10-09T16:58:58Z
updated_at: 2026-10-09T17:11:03Z
---

URGENT (Micah, 2026-10-09). Amended the same hour: the deployment's crew name is scrubbed too (standing ruling of 2026-09-19). This repo is public and its examples carry a real company name, a real person's name and what look like real Google ids. Replace them with Marigold fixtures. Mechanical; no behavior change.

## Scope

Every tracked file in `isaac-google`: features, specs, feature-steps, src docstrings and messages, resources, README, docs.

## Replacements — use exactly these, so the repos stay consistent

| Find (case-insensitive) | Replace with |
|---|---|
| the company's domain | `marigold.example` |
| the company's name anywhere else (organization id, project ids, session names, config paths) | `marigold` |
| the operator's full name | `Hieronymus Finch` |
| the operator's first name used as a sample address, sender, user or query | `hieronymus@…`, `hieronymus-finch`, `hieronymus` |
| `skiff` anywhere (the account `skiff@…`, `users/skiff`, project and session names such as `…-skiff`, prose) | `isaac` (`isaac@marigold.example`, `users/isaac`, `marigold-isaac`) |
| any long numeric `users/<digits>` id | `users/100000000000000000001` (keep distinct ids distinct: …002, …003) |
| any `domainId` / customer id that is not obviously fake | `0marigold` |

Leave alone: decision attributions in comments and feature headers of the form "Decision (date, Micah)" or "(Micah, date)" — those are not examples and are a separate ruling.

Error messages and manifest descriptions that tests assert on must change together with their tests.

## Acceptance

- `git grep -i` for the company's name prints nothing.
- `git grep -i` for the deployment's name prints nothing.
- `git grep -i "micah"` prints only decision-attribution lines.
- `git grep -E "users/[0-9]{12,}"` prints only the replacement ids.
- The repo's full verification is green (`bb verify`, and `bb jvm-spec` / features where the repo has them).
- No pin, dependency or behavior change. One commit.

## Likely repo scope

`isaac-google`.

## Worker conflict (2026-10-09)

The scope requires rewriting example data in `features/*.feature` (for example `features/people.feature:17` and `features/health.feature:15`), while the assigned `hail-bean-work-gate` skill forbids any `.feature` edit except removing `@wip`. These are not `@wip` features and the bean carries no feature baseline. Cannot satisfy both instructions without planner clarification of this scrub exception. Separately `LICENSE:3` has a legal copyright attribution to Hieronymus Finch; replacing legal ownership is not a fixture scrub, but the literal grep acceptance allows only decision attributions. Please explicitly rule on the license exception or authorize changing the legal attribution. No implementation edits made pending planner resolution.

## Ungated

No feature runner contract to baseline. The features are the examples being scrubbed, not a frozen behavior contract. `bb bean-gate verify` will exit 2. That is expected.

## Planner note (2026-10-09)

The `@wip`-only rule applies to a **baselined** bean. This one has no `feature-baseline:`. Scrub identifiers in the feature files the acceptance names. Do not add, delete, or reword a scenario. Close ungated: `beans update isaac-i0eh --tag=unverified`, stay `in-progress`, hail `isaac-verify`. Do not land it yourself and do not mark it `completed`.

`LICENSE` is a copyright notice, not an example. Leave it. The micah grep may also print that one copyright line. Everything else named Micah is either a decision attribution or gets scrubbed.


## Worker handoff (2026-10-09)

isaac-google branch: bean/isaac-i0eh @ 40442b391c84133c36176df92b631a087f30c42f (base origin/main@342da242baf29256d5ef8636ad1ae85a90a9b44d). Single implementation commit. Replaced sample organization, sender, email, tenant, config and prose identifiers across features, specs, source examples, manifest, dependency comment and rollout docs; left LICENSE and decision attribution intact. No scenario added, removed, or reworded except identifier substitution; no dependency pin or behavior change.

Checks: bb jvm-spec (268 examples, 0 failures); bb features (44 examples, 0 failures); bb ci (exit 0); git diff --check (clean); git grep -i for the company and deployment names (none); git grep -i micah (LICENSE:3 copyright and src/isaac/google/door.clj:81 decision attribution only); git grep -E "users/[0-9]{12,}" (none); bb bean-gate verify isaac-i0eh (exit 2, not gated). bb verify is not a task in isaac-google bb.edn; attempting it reports File does not exist: verify.



## Landed on main (2026-10-09)

main-sha: isaac-google 06e4f0fccfdfa0801d2a6ad1c9af0fd8a881076e

Verification: bb ci (268 specs, 44 features), bb jvm-spec (268), bb features (44): all green; identifier greps clean except approved LICENSE and decision attribution. One mechanical scrub commit; no pin change. Historical test smells: spec/isaac/google/token_spec.clj:88 Thread/sleep, spec/isaac/google/module_spec.clj:12 slurp resources/isaac-manifest.edn. Test logs include existing structured warnings and expected CLI startup banners.
