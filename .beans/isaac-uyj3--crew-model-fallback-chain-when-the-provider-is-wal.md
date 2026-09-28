---
# isaac-uyj3
title: Crew model fallback chain when the provider is walled
status: draft
type: feature
priority: high
tags:
    - agent
created_at: 2026-09-28T14:10:02Z
updated_at: 2026-09-28T14:10:02Z
---

A crew names an ordered fallback chain. When the model at the head of the chain is unavailable, the turn continues on the next model that can take it. The transcript and the tool results already written stay. The tools are not run again.

Today a 429, a 503, a usage limit, a billing wall, an auth rejection, or a stalled stream ends the turn and Discord says the provider is broken. Context overflow already compacts and retries the same model. A 400 that means the request was built wrong, such as `instructions` together with `previous_response_id`, stays a failure. Falling over would hide that and spend a second model on it.

## Decision (2026-09-28, Micah)

The chain lives on the crew. `:model` stays the head. `:fallback` is an ordered seq of model ids, each of which must exist. A model may name a different provider. Zane can be `:grok-4-6`, then `:grok-4-7`, then a Claude model.

Fall back only when the provider wall says the model is unavailable (`:unavailable?` with reason `:wall`, `:auth`, or `:stream-stalled`). Do not fall back on a genuine `:api-error` such as a 400 contract rejection. Do not fall back on `:context-overflow`. That path still compacts and retries the same model.

A failure on a later tool cycle continues from the transcript. Tool results already persisted are not executed again. The fallback request does not send `previous_response_id`. That id belongs to the model that stored it.

The model that answers keeps the rest of the turn. The next user message returns to the head of the chain, unless that provider is still inside its retry-after. The wall is per provider, so a walled provider skips every later model on that same provider. An empty Grok balance is not fixed by trying another Grok model. A fallback model whose context window cannot hold the transcript is skipped.

The jump is logged with the skipped model and the reason. The transcript records which model wrote the reply. Discord says the provider is broken only when the chain is exhausted.

## Scenario plan (not yet written)

1. A walled primary continues the turn on the next model. Tool results already written are not run again. The reply is stamped with the fallback model.
2. A 400 contract error does not fall back.
3. Context overflow does not fall back. The same model is compacted and retried.
4. A failure after tools have run continues from the transcript on the next model.
5. The fallback request carries no `previous_response_id`.
6. The rest of the turn stays on the model that answered.
7. The next user message returns to the head of the chain.
8. While the head provider is still inside its retry-after, the next message starts on the next provider and does not call the walled one.
9. A later model on the walled provider is skipped.
10. A fallback model that cannot hold the transcript is skipped.
11. The chain exhausted is the turn that reports the provider broken.
12. The jump log names the skipped model and the reason.

Promote to `todo` only after those scenarios are committed `@wip` and `bb bean-gate baseline` has frozen them.
