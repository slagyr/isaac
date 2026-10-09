---
# isaac-izrn
title: 'isaac-gchat: scrub real names from examples — Marigold fixtures only'
status: todo
type: task
priority: critical
created_at: 2026-10-09T16:58:58Z
updated_at: 2026-10-09T16:58:58Z
---

URGENT (Micah, 2026-10-09). This repo is public and its examples carry a real company name, a real person's name and what look like real Google ids. Replace them with Marigold fixtures. Mechanical; no behavior change.

## Scope

Every tracked file in `isaac-gchat`: features, specs, feature-steps, src docstrings and messages, resources, README, docs.

## Replacements — use exactly these, so the repos stay consistent

| Find (case-insensitive) | Replace with |
|---|---|
| the domain `tonotop.com` | `marigold.example` |
| `tonotop` anywhere else (organization id, project ids, session names, config paths such as `google.tonotop.…`) | `marigold` |
| `Micah Martin` | `Hieronymus Finch` |
| `micah@…`, `micah-martin`, bare `micah` used as a sample sender, user or query | `hieronymus@…`, `hieronymus-finch`, `hieronymus` |
| any long numeric `users/<digits>` id | `users/100000000000000000001` (keep distinct ids distinct: …002, …003) |
| any `domainId` / customer id that is not obviously fake | `0marigold` |

Leave alone: decision attributions in comments and feature headers of the form "Decision (date, Micah)" or "(Micah, date)" — those are not examples and are a separate ruling. Leave `yopp` alone.

The README session-name examples and the DM session names in features change together.

## Acceptance

- `git grep -i tonotop` prints nothing.
- `git grep -i "micah"` prints only decision-attribution lines.
- `git grep -E "users/[0-9]{12,}"` prints only the replacement ids.
- The repo's full verification is green (`bb verify`, and `bb jvm-spec` / features where the repo has them).
- No pin, dependency or behavior change. One commit.

## Likely repo scope

`isaac-gchat`.
