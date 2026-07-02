---
# isaac-s0ho
title: Session identity in the cached system prompt (Session + Crew, every surface)
status: completed
type: feature
priority: normal
created_at: 2026-07-02T18:29:51Z
updated_at: 2026-07-02T19:35:59Z
---

## Context / Motivation

Turns currently learn their own session/crew only via session_info or per-turn hail guidance (which is ephemeral and hail-only). Identity is turn-independent — it belongs with soul/boot-files/rules in the system prompt, not in per-turn framing.

Decision (2026-07-02, Micah): identity is ambient (system prompt); events are per-turn (trusted block). Rationale: system text is stable per session -> rendered once per request, part of the cached prefix (zero marginal cost per turn), never stored in the transcript. Per-turn guidance would repeat identical text every turn and vanish from history each turn.

## Design

- build-system-text (isaac.llm.prompt.builder) gains a session-identity block: Session: <name>, Crew: <crew>.
- Rendered on EVERY turn, every surface (hail, discord, imessage, cron, cli, acp).
- Stable ordering alongside soul/boot-files/rules so an unchanged session never busts the prompt cache.
- Origin stays per-turn in the existing trusted block (genuinely per-turn data).

## Acceptance scenarios (committed @wip)

isaac-agent `features/prompts/session-identity.feature` — 3 scenarios:
1. identity present in cached system prompt (system[0].text + cache_control)
2. byte-identical system text across turns (cache stability) — uses one NEW step: "the system text of the last N chat requests on session X is identical" (approved 2026-07-02)
3. transcript stays clean (exact-match user/assistant content)

Acceptance: `bb features features/prompts/session-identity.feature` green (after removing @wip); `bb spec` green.

## Likely repo scope

isaac-agent (llm/prompt/builder.clj, charge plumbing for crew/session-name).


---

## Resolution (unverified — for verifier)

Implemented in isaac-agent `main` commit **0a92e22** (base 50eb225).

**Where identity is rendered** — `build-system-text`
(`src/isaac/llm/prompt/builder.clj`) gains a `session-identity-block`
(`Session: <name>` / `Crew: <crew>`), placed in the cached system prefix after
soul/boot-files/rules/skill-menu and before the injection guard. `build-system-text`
keeps a 5-arg arity (delegates to the 7-arg with nil identity) so any caller I
didn't touch degrades cleanly.

**Plumbing (charge already carried both fields):** `:crew` and `:session-key`
live on the charge. Threaded `:session-name` (= session-key) and `:crew` through
`drive/turn` `execute-llm-turn!` → `build-chat-request` → `api/build-prompt` into
both builders — `prompt/build` (builder, used by grover/ollama/openai/responses)
and `messages/build` (anthropic). Because everything routes through
`execute-llm-turn!`, all surfaces (hail/discord/imessage/cron/cli/acp) get it for
free. Origin stays per-turn in the existing trusted block — untouched.

**Feature** (`features/prompts/session-identity.feature`, landed un-@wip, 3
scenarios all green):
1. identity present in `system[0].text` + `cache_control.ephemeral` (synthetic
   build path — `grover:anthropic` routes to `messages/build`, giving the block
   shape the scenario asserts).
2. byte-identical system text across 2 turns — the NEW step `the system text of
   the last N chat requests on session "X" is identical`. Implemented by
   accumulating each completed turn's chat request per session in
   `record-turn-result!` and comparing extracted system text (tolerant of both
   `:system` block-array and messages[0] system-role shapes).
3. transcript stays clean — identity never persisted (exact user/assistant match).

**Test-support touched:** `session_steps/build-session-prompt` now passes
`:session-name`/`:crew`; per-turn request accumulator + the new step/extractor.

**One existing spec updated (legitimately):** `drive/turn_spec` "replays prior
transcript…" and "…context-mode reset" asserted exact `:messages` system content
("You are Brain."/"You are Pinky."). Those exercise the real turn path, which now
appends identity — updated the expected system text to include the Session/Crew
lines and added `:crew` to their synthetic charges (matching the sessions' actual
crews). No behavior change beyond the feature itself.

**Verification:** isaac-agent `bb verify` — config-bypass-lint ok; **1125 spec
examples / 2209 assertions, 0 failures**; **563 feature examples / 1260
assertions, 0 failures**; `bb lint` clean on touched files.
