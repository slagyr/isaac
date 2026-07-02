---
# isaac-reg1
title: Bean-work hail payload should include :repo and repo-local verify fallback should exist
status: scrapped
type: task
priority: low
created_at: 2026-06-29T17:53:31Z
updated_at: 2026-07-02T00:32:13Z
---

## Summary
Fresh orchestration run `isaac-orc1` showed that the delivered `isaac-work` hail still omits `:repo`, and there is still no repo-local verifier-handoff fallback doc/skill available to the worker in the checked-out toolbox. The worker could recover, but only by inferring repo context from the clone and reconstructing the verify handoff contract from scattered docs/history.

## Why this matters
A hail-driven worker should be able to bootstrap from the delivered hail + repo-local docs without guesswork, especially once work spans multiple sibling repos.

## Desired outcome
- `isaac-work` delivery payloads consistently include `:repo` for bean work.
- Repo-local fallback docs include the verifier handoff contract (or a `hail-bean-verify` skill/file) alongside `hail-bean-work`.
- A worker can discover the expected verify hail payload shape without searching bean history.

## Acceptance ideas
- Add/verify a spec or feature proving bean-work hails include `:repo` in payload.
- Add repo-local verifier fallback docs or skill file under `.toolbox/skills/`.
- Update worker bootstrap docs to describe the verifier handoff payload fields for this project.

## Origin
Surfaced during process-test bean `isaac-orc1` fresh hail-driven run on 2026-06-29.
