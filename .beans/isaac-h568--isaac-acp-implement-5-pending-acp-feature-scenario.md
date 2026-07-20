---
# isaac-h568
title: 'isaac-acp: implement 5 pending ACP feature scenarios (missing step impls)'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-19T18:37:12Z
updated_at: 2026-07-20T00:20:24Z
---

Parent: isaac-xapx (spun off from the isaac-7ivl runner conversion).

## Goal

Implement the 5 pending `isaac-acp` feature scenarios that lack step
definitions (gherclj "not yet implemented"). These are pre-existing product
debt surfaced — not caused — by the xapx native-runner conversion (isaac-7ivl);
they were pending under the old JVM `bb features` path too.

## Pending scenarios (missing step impls)

1. ACP command tool notifications arrive before the final response in stdout
2. ACP command `acp` uses workspace SOUL.md when no soul in crew config
3. ACP Provider Error Surfacing: connection refused error is surfaced to the client
4. ACP Turn Cancellation: `session/cancel` arrival is logged at info
5. ACP Error Response Format: connection refused error is sent as
   `agent_message_chunk` with `end_turn`

## Acceptance

- [ ] Step definitions implemented for the 5 scenarios above (and any product
      behavior they assert).
- [ ] `bb features` in isaac-acp green with these scenarios no longer pending.
- [ ] `bb ci` green in isaac-acp.

## Notes

- Runner wiring is already done and at parity (isaac-7ivl @ isaac-acp
  `1a81c6c`). This bean is product/step work only — do not touch bb.test-tasks
  wiring.
- Separate from the 1 intentional spec pending
  (`spec/isaac/comm/acp/server_spec.clj:398`, Micah `db83cb30`, 2026-05-21) —
  that is a deliberate "do not block CI" pending and stays as-is unless a
  dedicated bean revisits the snapshot-capture investigation.


## Implementation (scrapper@isaac-work-2)

The 5 pending scenarios lacked step-namespace wiring (and one missing phrase), not product code:

1. tool notifications — needs `isaac.tool.tools-steps` (`the built-in tools are registered`)
2. workspace SOUL.md — added `workspace {crew} in {home} has SOUL.md:` in acp_steps
3. connection refused (provider_errors + error_response) — needs `isaac.llm.providers-steps`
4. session/cancel log — needs `isaac.foundation.log-steps`

Wired those `-s` namespaces into deps.edn :features and bb.edn features step-globs.

isaac-acp main **0a6143f**. Verified: bb features 61/0 (136 asserts), bb ci green (spec 70/0 + 1 intentional pending at server_spec:398).
