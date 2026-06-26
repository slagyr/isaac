---
# isaac-4e4b
title: Uniform session selection + parameter override across tools (hail/prompt/acp/chat)
status: draft
type: epic
priority: normal
created_at: 2026-06-26T04:14:17Z
updated_at: 2026-06-26T04:14:30Z
---

DESIGN (exploratory, 2026-06-25). Session SELECTION and PARAMETER OVERRIDE are a cross-cutting concern shared by hail, the prompt command, ACP, and chat. Each rolls its own. Unify into one mechanism.

## Finding (grounded in code)
- Override half is ALREADY shared. All tools funnel session creation through session.context/create-with-resolved-behavior! (agent), which applies a fixed set of behavioral-keys (crew, model, effort, tags, context-mode, ...). hail, cron, prompt all use it. So "override session parameters" is already common — just not surfaced consistently per tool.
- Selection half is DIVERGENT — the real gap.
  - hail has the richest selector: the FREQUENCY — :session ids, :session-tags, :crew (sessions-of-crew, per kt1m), :reach :one/:all, :spawn-session. Resolves to 0..N sessions, create-if-spawn.
  - prompt (bridge/prompt_cli) is ad-hoc: explicit :session id, else most-recent, else "prompt-default" key. No tags, no crew, no attribute selection.
  - ACP / chat each have their own attach logic.
  So prompt/acp/chat cannot even say "the session of crew main" or "a session tagged X" — capabilities hail's frequency already has. Unifying LEVELS THEM UP, not just consolidates.

## Proposed shape
Extract a tool-agnostic session selector (generalize the hail frequency) paired with the already-shared override:
  :select   = {:session [...] :session-tags [...] :crew X :reach (:one|:all) :create (:always|:if-missing|:never)}
  :override = {:crew ... :model ... :effort ... :context-mode ...}   (the behavioral-keys)
- hail IS this already (frequency = :select; async fan-out back-end).
- prompt/acp/chat swap ad-hoc logic for resolve(:select) + create-with-resolved-behavior!(:override), with per-tool DEFAULTS (prompt -> :reach :one, :create :if-missing, recent-fallback).
- A shared resolver: (resolve-session-targets select store) -> sessions (create per policy). Each tool keeps only its back-end (hail delivers async to N; prompt/chat/acp attach to the 1).

## Differences to absorb cleanly
- :reach :all (only hail fans out; others constrain to :one).
- create-policy (hail spawn is opt-in via :spawn-session; prompt creates-by-default) -> the :create knob.
- prompt recent/default fallback -> a selector mode (:recent) or a per-tool default layered on top? (decide)

## Open questions
- Naming/home: "frequency" is a hail-ism (radio). Generalized = session selector / target / address. Lives in foundation (every module shares) or agent?
- Is :recent a first-class selector mode or a tool default?
- Migration order (prompt first — simplest), and whether hail frequency literally becomes the shared type or maps to it.

## Sequencing / relationship
- Do AFTER ebm2 + kt1m (they just reshaped the frequency; generalize the SETTLED shape). Both landed.
- Incremental: extract shared selector + resolver, migrate one tool at a time (prompt -> chat/acp -> hail), not big-bang.
- This is the MANUAL precursor to the conversation-recall (RAG) vision: unify selection first (explicit), then make it implicit/automatic (recall-driven), and the session dissolves into the background.

Status draft — design only, no scenarios; revisit before promoting. Children (later): shared selector type + resolver; migrate prompt; migrate chat/acp; map hail frequency onto it.

Related: the conversation-recall (RAG) bean isaac-51xy is the taken-to-its-conclusion version of this (manual selection -> implicit recall-driven).
