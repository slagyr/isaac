---
# isaac-nbgn
title: Shared session selector + resolver + override (isaac-agent) — proven by migrating prompt
status: in-progress
type: feature
priority: normal
created_at: 2026-06-26T16:28:33Z
updated_at: 2026-06-26T17:42:49Z
parent: isaac-4e4b
---

KEYSTONE of isaac-4e4b. Build the tool-agnostic session selection/override mechanism in isaac-agent, and migrate the `prompt` command onto it as the first consumer (proving the API end-to-end before chat/acp/hail depend on it). Flag-design contract is in the parent isaac-4e4b ("SETTLED flag design").

## Build (isaac-agent)
- **:select map + resolver.** A `resolve-session-targets` that takes a :select map `{:session [...] :session-tags [...] :crew X :reach :one :create (:never|:if-missing|:always)}` + the session store and returns the target session(s), applying the tri-state create policy. Reuse hail's existing matching logic where possible (don't reimplement).
- **Tri-state create (`:create`):** :never = match existing only (no match -> error for sync tools); :if-missing = match-or-create (== hail's :spawn-session; the default for prompt); :always = always fresh. CLI surface is a single `--create never|if-missing|always`. When creating with describe attrs (crew/session-tags), those become the new session's identity.
- **Override path (`--with-*`):** map --with-crew/--with-model/--with-effort/--with-context-mode/... onto the behavioral-keys and apply via the existing session.context/create-with-resolved-behavior! (don't invent a parallel mechanism).
- **Shared CLI flag helpers + validation rules:** parse --session (exact) / --crew / --session-tag (describe, AND) / --create / --with-*; enforce: --session mutually exclusive with --crew/--session-tag/--create (fail fast, clear message); describe flags compose; --reach is NOT exposed here (hail-only — sync tools assume :one).
- **Name the abstraction** (prereq): "session selector" / "target" / "address" — NOT the hail-ism "frequency". Decide and use consistently.

## Migrate `prompt` (the proving ground)
- Replace bridge/prompt_cli's ad-hoc session selection (explicit :session / most-recent / "prompt-default") with the shared selector + flags. prompt gains --crew/--session-tag selection + --with-* override (new capabilities). No --reach (assumes :one). Keep -R/--resume as the recent-fallback (or fold into the selector as a default — decide).

## Acceptance
- A shared agent-side selector/resolver/override module exists with unit + feature coverage (the validation rules, tri-state create, --with-* override, describe-AND, --session-exclusivity errors).
- `prompt` uses it: `isaac prompt --crew main -m ...`, `--session-tag X -m ...`, `--create always --crew marvin -m ...`, `--session bridge --with-model opus -m ...` all work; illegal combos error clearly.
- Existing prompt behavior preserved (explicit --session, resume).
- The abstraction is named and documented; flag contract matches isaac-4e4b.

## Notes
First of 4 children (B1). B2 chat, B3 acp, B4 hail reconcile follow (blocked by this). Pairing core+prompt avoids designing the lib in a vacuum. Surfaced 2026-06-26.

## Behavioral spec written (2026-06-26, @wip)

Reviewed one-at-a-time with Micah and committed to `isaac-agent/features/bridge/cli-prompt.feature` as `@wip` (excluded from generation until B1 lands; removing `@wip` is the DoD):

- `--crew` targets the crew's existing session (selector, **reuse**)
- `--crew` with no match **creates** one (default `:create :if-missing`)
- `--crew` with multiple matches **resumes the most recent** (`:reach :one`)
- `--session-tag` selects by tag; repeated `--session-tag` **AND-composes**
- `--create never` with no match **errors** (no fallback create)
- `--create always` starts a **fresh** session even when one matches
- `--with-model` **overrides** the model for the turn (orthogonal to `--crew` selection)
- `--session` + selection flags -> **usage error**

Also rewrote the two pre-existing `--crew` scenarios (model-resolution, soul) — they used to land on the fixed `prompt-default` session; under the selector model they land on the crew-owned session. Both now `@wip`.

### Still to author during implementation
- speclj **unit specs** for the shared resolver + validation (the `resolve-session-targets` API + `--session`-exclusivity / describe-AND / tri-state-create rules) — deferred because the resolver's namespace/fn signature isn't designed yet; write them alongside the resolver so they test a real API rather than an invented one.
