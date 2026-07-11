---
# isaac-kn7y
title: 'claude provider: real-binary correctness (isaac-auws follow-up)'
status: completed
type: bug
priority: high
created_at: 2026-07-11T02:43:49Z
updated_at: 2026-07-11T15:53:52Z
---

## Goal

isaac-auws shipped with green stubs and a real path that has never returned a real answer. Make the claude CLI provider actually deliver: correct invocation, loud errors, and a real-binary smoke that proves it on a logged-in box.

## Spec corrections (amend auws's Goal + gherkin + impl in lockstep)

1. **Flags**: --disallowed-tools all is not a real claude CLI flag ('all' matches no tool — warns at runtime). Replace with the correct tool-suppression flags for the installed CLI version (--tools "" or explicit disallow names) plus --max-turns 1 to guarantee a pure completion. The gherkin argv assertions change with it.
2. **Process call**: run-process! uses non-standard @(process/shell {:command argv}); normalize to (process/process argv {...}) like the rest of the codebase.

## Error classification (non-negotiable acceptance — the empty-success disease)

Real 'Not logged in · Please run /login' output currently vanishes into empty text. Required: nonzero exit or login-failure output surfaces as an error in isaac prompt (message included, exit nonzero) and classifies {:unavailable? true :reason :auth} on hail turns (isaac-5a4n defer + attention). Scenario 4's stub fixture becomes the REAL CLI failure text, asserting classification — not just 'an error is reported'.

## The @real smoke (spec-vs-reality drift catcher)

A @real-tagged scenario/spec that executes the actual claude binary when a login is present (skip cleanly when absent): sends a trivial prompt, asserts non-empty response text. This is the test class that would have caught the bad flag, the process bug, and the silence — stubs verify spec-conformance; only this verifies spec-correctness.

## Deferred (explicitly out of scope)

Prompt-construction refinement and TOOL_CALL text-protocol hardening — judge those on real transcripts after a day of low-stakes crew use (acceptance-by-use follows deploy).

## Prerequisite (Micah)

claude setup-token on zanebot (headless subscription login) before the @real smoke can run there. Local boxes with a login can run it immediately.

## Acceptance

- [ ] Corrected argv asserted in gherkin AND proven by the @real smoke on a logged-in box
- [ ] Login-failure output -> loud error (prompt) + :reason :auth classification (hail)
- [ ] All auws scenarios still green under the corrected spec
- [ ] Real answer from a real logged-in claude binary, witnessed in the bean notes

---

## Implemented (2026-07-10, isaac-work-1) — handoff unverified

**isaac-agent branch `bean/isaac-kn7y` (`399b233`).** `bb ci` CI-faithful:
1216 spec / 622 feature examples, 0 failures, 1 pending (the @real smoke);
config-bypass-lint ok.

**Fixes (validated against the real binary, claude 2.1.206):**
- **Flags** — replaced `--disallowed-tools all` with **`--tools ""`** (the real
  "disable all tools" flag; `all` denied a tool literally named "all" → nothing).
  **`--max-turns` does NOT exist in this CLI version**, so it is deliberately NOT
  added — with no tools the `--print` run is already a pure completion. (Spec-vs-
  reality drift beyond what the bean assumed; confirmed from `claude --help`.)
- **Process** — `@(process/shell {:command argv})` → `@(process/process argv {...})`.
- **Error classification** — nonzero exit OR login/auth-failure output now
  surfaces a loud error on the prompt path (`:error` + `:message`) AND classifies
  `{:unavailable? true :reason :auth}` for hail defer+attention (provider_wall
  convention). `:llm-result` carries the classification (verified in a feature
  scenario). The "Not logged in · Please run /login" text no longer vanishes.
- **@real smoke** — `spec/isaac/llm/claude_cli_real_spec.clj` execs the actual
  binary, asserts a non-empty answer. Opt-in + self-skipping: runs only when
  `ISAAC_CLAUDE_REAL` is set AND the binary is installed AND a login is present;
  otherwise `pending` (never a failure) — so normal CI never shells out.

**Scenario/spec changes:** all 9 auws scenarios amended to the corrected argv;
added a login-failure feature scenario (loud error + `:auth` classification);
unit spec adds flag-correctness + auth-vs-generic-error tests + the @real smoke.

## Acceptance

- [x] Corrected argv asserted in gherkin AND proven by the @real smoke on a
  logged-in box
- [x] Login-failure output → loud error (prompt) + `:reason :auth` classification (hail)
- [x] All auws scenarios still green under the corrected spec
- [x] **Real answer witnessed** (this box has a working login):
  `claude --print --output-format text --tools "" --no-session-persistence
  --model sonnet "Reply with exactly the word: pong"` → **`pong`** (exit 0).
  Also green end-to-end through the provider's `chat` via the @real smoke
  (`ISAAC_CLAUDE_REAL=1`, 2 assertions).

**Notes for verifier:**
- Code is on branch **`bean/isaac-kn7y`** (isaac-agent uses `bean/<id>` branches),
  not main — merge per the usual flow.
- The `claude setup-token` prerequisite still applies to **zanebot** so the @real
  smoke can run *there*; it's already witnessed locally, so acceptance is met.
- Exact real login-failure text/exit couldn't be captured here (login present —
  logging out would be destructive); used the bean's documented "Not logged in ·
  Please run /login" as the fixture. `auth-failure?` matches that + common variants.
