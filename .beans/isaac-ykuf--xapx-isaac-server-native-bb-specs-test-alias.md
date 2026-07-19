---
# isaac-ykuf
title: 'xapx: isaac-server — native bb specs (:test alias)'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T18:13:56Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-server** to native bb specs via the shared runner.

## Wrinkle
Its tasks shell `clojure -M:test:spec` / `-M:test:features` — the `:test` alias adds classpath (spec-support, test deps). The native bb.edn must carry those same deps/paths or specs won't resolve. Likeliest module to hold JVM-only specs → keep `jvm-spec`/`jvm-features` and route any that fail native.

## Acceptance
- [x] `bb spec` / `bb features` native (no `clojure -M`), streamed, with the `:test`-equivalent classpath present.
- [x] PARITY: full suite native == JVM results; JVM-only specs routed to `jvm-*`, documented.
- [x] `bb ci` native; before/after wall-clock recorded here.

## Implementation (scrapper@isaac-work-2, 2026-07-19)

Committed directly to `main` on isaac-server @ `08b51e965afd65efe02e17e4c6f9dd6d3931ac11`.

- bb.edn uses shared `bb.test-tasks` from isaac-foundation-test-support @ `43cf46e` (`run-spec!` / native features / ci shell).
- Product foundation pin bumped `9b8ac71` → `d4a7bf10` (agent parity) so test-support `cli_steps` color API (`force-color?` / `console?`) resolves; only test-support stays at `43cf46e` for the runner.
- `:test`-equivalent classpath inlined on bb.edn: `spec` + `test-resources` + `spec-support/src`, c3kit-wire, telly module, marigold bridge/longwave, data.xml, http-kit, speclj, gherclj, babashka.process.
- Tasks: `spec`, `features`, `ci`, `verify` native; `jvm-spec` / `jvm-features` fallbacks with `clojure -M:test:*`.
- Features task uses a 180s budget (JVM shell was ~82s wall; shared 60s timeout too short).
- No `clojure -M` shell-out on the default path; output streams live.
- No JVM-only specs found — full suite passes native (http-kit stubs cleanly under bb).
- Collateral fixes required for green baseline:
  - restore `clear-sink-config!` / `server-sink?` (broken rename in `1a9a4fc`)
  - require `isaac.config.paths` in `change_source_bb` (native load)

### Wall-clock (this machine)

| path | wall |
|------|------|
| BEFORE bb spec (JVM shell) | real **8.58s** (171 ex / 0 fail) |
| BEFORE bb features (JVM shell) | real **82.10s** (55 ex / 0 fail) |
| AFTER bb spec (native) | real **3.92s** (171 ex / 0 fail) |
| AFTER bb features (native) | real **53.95s** (55 ex / 0 fail) |
| AFTER bb ci (native) | real **58.05s** |
