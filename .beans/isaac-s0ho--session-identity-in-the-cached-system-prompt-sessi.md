---
# isaac-s0ho
title: Session identity in the cached system prompt (Session + Crew, every surface)
status: draft
type: feature
created_at: 2026-07-02T18:29:51Z
updated_at: 2026-07-02T18:29:51Z
---

## Context / Motivation

Turns currently learn their own session/crew only via session_info or per-turn hail guidance (which is ephemeral and hail-only). Identity is turn-independent — it belongs with soul/boot-files/rules in the system prompt, not in per-turn framing.

Decision (2026-07-02, Micah): identity is ambient (system prompt); events are per-turn (trusted block). Rationale: system text is stable per session -> rendered once per request, part of the cached prefix (zero marginal cost per turn), never stored in the transcript. Per-turn guidance would repeat identical text every turn and vanish from history each turn.

## Design

- build-system-text (isaac.llm.prompt.builder) gains a session-identity block: Session: <name>, Crew: <crew>.
- Rendered on EVERY turn, every surface (hail, discord, imessage, cron, cli, acp).
- Stable ordering alongside soul/boot-files/rules so an unchanged session never busts the prompt cache.
- Origin stays per-turn in the existing trusted block (genuinely per-turn data).

## Acceptance scenarios

To be written @wip in isaac-agent features/ (assertion machinery: "the prompt X on session Y matches" with system[0].text + cache_control, per features/prompts/rules.feature).

## Likely repo scope

isaac-agent (llm/prompt/builder.clj, charge plumbing for crew/session-name).
