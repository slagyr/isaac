---
# isaac-nbgn
title: Shared session selector + resolver + override (isaac-agent) — proven by migrating prompt
status: todo
type: feature
priority: normal
created_at: 2026-06-26T16:28:33Z
updated_at: 2026-06-26T16:28:33Z
parent: isaac-4e4b
---

KEYSTONE of isaac-4e4b. Build the tool-agnostic session selection/override mechanism in isaac-agent, and migrate the `prompt` command onto it as the first consumer (proving the API end-to-end before chat/acp/hail depend on it). Flag-design contract is in the parent isaac-4e4b ("SETTLED flag design").

## Build (isaac-agent)
- **:select map + resolver.** A `resolve-session-targets` that takes a :select map `{:session [...] :session-tags [...] :crew X :reach :one :create (:none|:spawn|:new)}` + the session store and returns the target session(s), applying the tri-state create policy. Reuse hail's existing matching logic where possible (don't reimplement).
- **Tri-state create:** :none = match existing only (none -> error for sync tools); :spawn = match-or-create (== hail :spawn-session); :new = always fresh. When creating with describe attrs (crew/session-tags), those become the new session's identity.
- **Override path (`--with-*`):** map --with-crew/--with-model/--with-effort/--with-context-mode/... onto the behavioral-keys and apply via the existing session.context/create-with-resolved-behavior! (don't invent a parallel mechanism).
- **Shared CLI flag helpers + validation rules:** parse --session (exact) / --crew / --session-tag (describe, AND) / --spawn / --new / --with-*; enforce: --session mutually exclusive with --crew/--session-tag/--spawn/--new (fail fast, clear message); describe flags compose; --reach is NOT exposed here (hail-only — sync tools assume :one).
- **Name the abstraction** (prereq): "session selector" / "target" / "address" — NOT the hail-ism "frequency". Decide and use consistently.

## Migrate `prompt` (the proving ground)
- Replace bridge/prompt_cli's ad-hoc session selection (explicit :session / most-recent / "prompt-default") with the shared selector + flags. prompt gains --crew/--session-tag selection + --with-* override (new capabilities). No --reach (assumes :one). Keep -R/--resume as the recent-fallback (or fold into the selector as a default — decide).

## Acceptance
- A shared agent-side selector/resolver/override module exists with unit + feature coverage (the validation rules, tri-state create, --with-* override, describe-AND, --session-exclusivity errors).
- `prompt` uses it: `isaac prompt --crew main -m ...`, `--session-tag X -m ...`, `--new --crew marvin -m ...`, `--session bridge --with-model opus -m ...` all work; illegal combos error clearly.
- Existing prompt behavior preserved (explicit --session, resume).
- The abstraction is named and documented; flag contract matches isaac-4e4b.

## Notes
First of 4 children (B1). B2 chat, B3 acp, B4 hail reconcile follow (blocked by this). Pairing core+prompt avoids designing the lib in a vacuum. Surfaced 2026-06-26.
