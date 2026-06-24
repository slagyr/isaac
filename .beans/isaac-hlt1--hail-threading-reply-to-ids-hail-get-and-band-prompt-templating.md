---
# isaac-hlt1
title: hail threading, reply-to, ids, hail_get, and band prompt templating with params
status: in-progress
type: feature
priority: normal
tags:
    - hail
    - agent
    - planning
    - unverified
created_at: 2026-06-23T23:00:00Z
updated_at: 2026-06-24T01:20:37Z
---

# Hail threading, reply-to, ids, and hail_get

## Context / Motivation
Hails (via bands like isaac-plan / isaac-work / isaac-verify and their corresponding hail-bean-* skills) are the primary way for autonomous crews to hand off work, ask questions (e.g. verifier needs planner resolution on a bean), and respond.

The band body is intended to act as the delivered prompt (so callers can hail a band without always supplying :prompt). If the hailer supplies :prompt it should override.

Currently hails have :id (auto-assigned sequential) returned on send, and deliveries carry :origin {:kind :hail :hail-id "..."}.

To support rich back-and-forth without stuffing full context into every prompt/params, we need:

- Auto :thread-id
- :reply-to linking
- A way for agents to fetch previous hails by id (hail_get tool)
- Search existing hail files (no separate index; just scan the hail/ subdirs)
- Ensure band body becomes the prompt when no :prompt is supplied in the hail call (templated with :params)

No access control for now (hails may contain sensitive data later).

## Goals
- Every hail record gets a unique :id (already mostly true) returned to the caller.
- :thread-id auto-populated:
  - Same as the hail's own :id if not supplied.
  - Inherited from the :thread-id of the :reply-to hail if :reply-to is given and :thread-id omitted.
- Optional :reply-to <hail-id> supported in hail records.
- New `hail_get` tool (models can call it) that finds a hail by id across all hail subdirectories.
- Band body used as default prompt for band-targeted hails (template + :params → rendered prompt; override if caller supplies :prompt).
- Thread/reply info carried through the hail record, deliveries, and made available in params/context.
- Search uses simple dir scan + read (grep friendly) — no dedicated index.

## Non-goals (for this bean)
- Automatic derivation of :reply-to from current session/turn origin (nice-to-have later; hailer can still supply it).
- Full access control / redaction on hail_get.
- Changing storage format.

## Acceptance Scenarios

See the following @wip feature files (organized by theme; total ~19 scenarios using Marigold examples and params/templating):

- isaac-hail/features/hail-threading.feature : core threading (auto thread-id, inherit on reply-to), carried fields, preserved in delivered, follow-up sends, agent context.
- isaac-hail/features/hail-get.feature : hail_get tool, searching (dir scan, any subdir), full records including templated ones (rendered prompt, params, sent-at).
- isaac-hail/features/hail-band-prompts.feature : band prompt templating with params (render template + params → rendered prompt, overrides, as turn input, send returns id, agent retrieval + follow-up workflows, search on templated, context).

Key references (approximate lines after split):
- Thread auto / inherit / follow-up: isaac-hail/features/hail-threading.feature
- Preserved / carried: isaac-hail/features/hail-threading.feature
- hail_get / search basics and templated: isaac-hail/features/hail-get.feature
- Band templating / render / input / agent: isaac-hail/features/hail-band-prompts.feature

The scenarios test unique aspects:
- Threading basics and inheritance (plain and with templating).
- hail_get and search (dir scan, any subdir, full fields including rendered prompt/params/sent-at).
- Band templating (template + params → rendered prompt; override; rendered as input).
- Preservation and carry-through in records, delivered, and agent context/turn.
- Send returns id for templated.
- Agent workflows using retrieval for follow-ups.
- Search and context for templated cases.

## Implementation Outline
- queue.clj / send! : ensure :id, auto :thread-id logic (lookup via new find-by-id if reply-to present)
- New lookup fn (e.g. in queue or a new hail/store.clj): scan the hail subdirs for <id>.edn, read it. Use simple fs walk + edn/read.
- tool/hail.clj : update hail-send-tool to accept :thread-id, :reply-to; auto-fill thread-id; document that for bands the band body supplies default prompt (templated).
- Add new tool "hail-get" (factory + handler) that calls the lookup and returns the record (or error). Register it in builtin or hail module tools.
- delivery_worker / router : when a delivery/hail targets a band and has no :prompt (or blank), render the band body template using the hail :params and inject as :prompt.
- Carry :thread-id / :reply-to through to deliveries and origin framing if useful.
- Update cli/http to accept/pass the new fields (thread-id, reply-to, params support).
- Add / update features (isaac-hail/features/hail-threading.feature, isaac-hail/features/hail-get.feature, isaac-hail/features/hail-band-prompts.feature) and specs.
- Update relevant skills (hail-bean-*) and souls to mention using :thread-id / :reply-to / hail_get / :params where it helps.
- No changes to access control or storage layout.

## Open Questions / Follow-ups
- Easy auto-:reply-to from current turn origin? (The delivery sets :origin {:kind :hail :hail-id "..."} — could expose via a cheap session/current-hail tool or inject into the skill context.)
- Should hail_get also support searching by thread-id or reply-to?
- Long-term: maybe a lightweight index or at least keep delivered hails for a while.
- Example usage in the isaac skills for verifier asking planner a question with reply-to.

## Size / Priority
Small-to-medium. Mostly additive. Good foundation for richer agent-to-agent conversations without prompt bloat.

(Bean drafted from conversation on 2026-06-23.)
