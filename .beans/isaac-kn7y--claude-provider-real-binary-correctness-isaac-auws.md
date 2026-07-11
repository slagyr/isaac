---
# isaac-kn7y
title: 'claude provider: real-binary correctness (isaac-auws follow-up)'
status: todo
type: bug
priority: high
created_at: 2026-07-11T02:43:49Z
updated_at: 2026-07-11T02:43:49Z
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
