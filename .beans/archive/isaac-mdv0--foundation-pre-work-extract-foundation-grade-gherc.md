---
# isaac-mdv0
title: 'Foundation pre-work: extract foundation-grade gherclj steps and split spec_helper'
status: completed
type: task
priority: normal
created_at: 2026-06-12T12:50:37Z
updated_at: 2026-06-12T16:09:38Z
parent: isaac-brth
---

Phase A step 12 of the isaac-foundation extraction (see isaac-brth reshaping
note). Foundation features (features/cli/*, features/module/* machinery
subset) currently use steps defined in server-flavored namespaces. Extract a
foundation-grade steps layer so those features can move at cut time; server
steps require + delegate (gherclj reports ambiguity if both repos later define
the same phrase).

- [x] New foundation step namespaces (matching the isaac.**-steps selector,
      e.g. spec/isaac/foundation/cli_steps.clj) holding exactly the steps the
      foundation features use:
      "isaac is run with {args}" (server-free main/run wrapper — the existing
      register-isaac-run-preflight! hook in
      spec/isaac/server/cli/cli_steps.clj moves with it),
      "an (empty )Isaac root at" (from session_steps.clj),
      "the isaac file ... exists with:" (from server_steps.clj),
      "Isaac root ... contains config" (from cli_steps.clj),
      "the config is loaded" (from config_steps.clj, sans the
      isaac.server.app require),
      stdout/exit-code assertions, step_tables helpers if used.
- [x] Server step namespaces require + delegate instead of redefining; run the
      gherclj ambiguity check.
- [x] Split src/isaac/spec_helper.clj: foundation scaffolding vs server store
      helpers (it currently requires isaac.session.store*).
- [x] Re-theme stray server requires in foundation specs so they can move
      later: main_spec requires isaac.session.store; module/loader_spec
      requires isaac.server.routes, isaac.comm.registry, isaac.hooks (use
      marigold berths instead).
- [x] Per-feature audit of features/{cli,module}/* for stray server-step usage
      (bb match-step per phrase).

## Acceptance

- bb spec and bb features green; gherclj ambiguity check clean.
- Foundation features and the foundation specs listed above require only
  foundation namespaces + the new step layer.

## Progress (banked 2026-06-12)

Done + pushed (green at each step): checkboxes 3 and 4 complete. Checkboxes 1
and 2 are partially done — the cli portion is fully extracted; checkbox 5 not
started.

Commits (all on main, bb spec + bb features + gherclj ambiguity green):
- b65def91 spec_helper split (checkbox 3)
- 5d2d96bf foundation specs drop server requires (checkbox 4)
- d8422e7d isaac-run postflight registry; LLM/HTTP/drive capture -> preflight(clears)+postflight(harvest)
- adbeb9af isaac-run clock binding -> register-isaac-run-wrapper! (memory-tool seam)
- 96114930 extract isaac.foundation.cli-steps (run wrapper + stdout/stderr/exit + stdin/command/clock + Isaac-root-config + isaac-file-contains); server/cli/cli_steps delegates (reply-*, background run, LLM/clock hook registrations); cli_steps_spec moved to foundation

The isaac-run wrapper is now foundation-clean: its three couplings (LLM/HTTP/
drive capture, memory-tool clock) attach via three foundation hook registries
(register-isaac-run-preflight!/postflight!/wrapper!). Server layers extend the
run without the foundation ns depending on isaac.llm.* / isaac.drive.* /
isaac.tool.memory.

Remaining (each: move phrases to a foundation ns, server requires+delegate,
keep gherclj ambiguity clean):
- session_steps: "an (empty )Isaac root at", "an empty Isaac state directory",
  "the file ... exists with:", "a module manifest ...:". RISK: initialize-root!
  mixes foundation resets with server/LLM teardown AND global mutation
  (alter-var-root on sidecar-store/create-store; remove-ns of isaac.comm.telly;
  comm-registry/tool-registry/single-turn resets). Needs a setup-hook untangle
  (foundation-minimal reset + server-registered hook), and backs 46 scenarios.
- server_steps: "the isaac file ... exists with:", "the EDN isaac file ...
  contains:", "the log has entries matching:" (confirm log helper is
  isaac.logger-only before moving).
- config_steps: config load/validation steps, dropping the isaac.server.app require.
- service_steps: the generic "the file ..." fs assertions (service_steps then
  requires+delegates for its own macOS use).
- Checkbox 5: per-feature audit of features/{cli,module}/* (bb match-step per phrase).

Approved decisions: incremental green sub-steps; isaac-run capture/clock/setup
relocated via hook registries (no phrase registered twice). Foundation/server
phrase divide approved (see the move list discussed on the bean's work session).

## Progress update 2 (config_steps done)

- 7ef2cd7e config_steps foundation-clean: loaded-config-has used app/current-config,
  which is literally (config/snapshot ...) (isaac.config.api — foundation). Swapped
  to config/snapshot directly, dropped the isaac.server.app require. config_steps now
  requires only foundation nses. Stays in place (config-namespaced = foundation).
  bb spec 1885 + bb features 744 green (incl. config hot-reload features).

Remaining shape (discovered via deeper reads — these share seams, best done as one
careful consolidated pass rather than piecemeal):
- A recurring config-change-notify seam: notify-config-change! -> runtime/notify-path!
  (config.runtime = server) appears in BOTH server_steps file-writes (isaac-file-
  exists-with, EDN-isaac-file) AND session_steps (file-exists-with, initialize-root!
  callers). Clean fix: foundation file-write steps do the foundation part (write +
  g/dissoc :feature-config) and run a registered post-write hook; the server layer
  registers (when source) runtime/notify-path!.
- server_steps log steps (log-entries-match/dont-match) are already foundation-clean
  (isaac.logger + step-tables + spec-helper only) — easy move.
- server_steps "EDN isaac file" steps carry phase machinery (isaac-file-phase) that
  entangles the move; needs care.
- session_steps root-setup: initialize-root! foundation resets + server teardown
  (comm/tool/single-turn/telly remove-ns/sidecar alter-var-root/memory-store) -> a
  root-setup hook; plus the shared-util ripple (mem-fs/root-dir/with-feature-fs used
  widely) — duplicate the trivial utils into the foundation ns to keep it self-contained.
- service_steps generic "the file ..." fs assertions -> foundation (likely clean, no notify).
- Then checkbox 5: per-feature audit (bb match-step per phrase).

## Progress update 3 (log + service-fs extracted; remaining core mapped)

Done + pushed since update 2 (each green + ambiguity-clean):
- 95879e9d foundation log-steps (the log has entries/no entries matching:) — clean,
  no fs-util coupling; server_steps_spec log tests moved to foundation/log_steps_spec.
- da6e6d28 foundation fs-steps (the file X contains:/exists/does not exist) from
  service_steps — clean, no notify; service_steps keeps expand-path for plist.

Foundation step layer now: isaac.foundation.cli-steps, .log-steps, .fs-steps; config_steps
foundation-clean in place. bb spec 1887 + bb features 744 green throughout.

REMAINING = the deeply-entangled core (each a careful sub-extraction, not a clean lift):
1. Simple file-writes ("the isaac file X exists with:" server, "the file X exists with:"
   session) — need a post-write config-change-notify HOOK seam in foundation fs-steps +
   a single server fixture-hooks ns to register (when source) runtime/notify-path! (avoids
   double-notify). Duplicate the trivial path utils (isaac-file-path/server-fs/with-server-fs)
   into foundation. MEDIUM.
2. EDN-file dual-mode ("the EDN isaac file X contains:/exists with:", "the isaac file X
   does not exist") — RIPPLE-HEAVY: shares a large write closure (parse-isaac-value,
   isaac-file-data, maybe-prune-root-entity!, dissoc-in, skip-row?, delete-sentinel?,
   isaac-file-path) with ~8 SERVER-only step bodies (configure, isaac-config-path-is,
   isaac-file-with-log-entries...). The closure is foundation-clean CODE but moving it
   means repointing those server steps to foundation/. Best done with the simple writes
   so the notify seam is uniform. HARD.
3. Root-setup ("an empty Isaac root at" x46, "an Isaac root at", "an empty Isaac state
   directory", "a module manifest ...:") — initialize-root! untangle: foundation resets +
   a root-setup hook for server teardown (comm/tool/single-turn/telly remove-ns/sidecar
   alter-var-root/memory-store). GLOBAL MUTATION; highest blast radius. HARD.
4. Checkbox 5: per-feature audit (bb match-step per phrase).

Recommended: do #1+#2 together (uniform notify seam + the closure repoint) as one focused
pass, then #3 (global-mutation untangle) as another, then #4. All four checkboxes (1,2,5)
remain open until these land.

## Progress update 4 (#1 file-writes + EDN done)

Done + pushed (each green + ambiguity-clean):
- f3ba4dd5 server isaac/EDN-file write steps -> isaac.foundation.fs-steps (duplicated
  write closure; config-change notify -> post-write hook registered by server_steps;
  5 file-write tests -> foundation/fs_steps_spec; dropped now-dead helpers).
- fd2c4e77 session file-exists-with -> foundation.fs-steps (feature-fs helpers); notify
  test relocated to server_steps_spec (where loading server-steps registers the hook),
  config-caching test repointed to foundation.

The notify seam: foundation isaac-file writes call notify-write! (g/dissoc :feature-config
+ run post-write hooks). server_steps registers (when :config-change-source) runtime/
notify-path!. Verified end-to-end in server_steps_spec.

Foundation step layer now: cli-steps, log-steps, fs-steps (file fixture/assert + isaac/EDN
file writes); config_steps clean in place. bb spec 1897 + bb features 744 green.

REMAINING:
- #3 root-setup: "an empty Isaac root at" x46, "an Isaac root at", "an empty Isaac state
  directory", "a module manifest ...:" — initialize-root! global-mutation untangle
  (alter-var-root sidecar-store, remove-ns telly, comm/tool/single-turn/memory-store
  resets) via a root-setup hook (foundation-minimal reset + server-registered teardown).
  Highest blast radius; the linchpin for foundation features.
- #4 (checkbox 5): per-feature audit of features/{cli,module}/* (bb match-step per phrase)
  to confirm the foundation subset routes only to the foundation layer.

## Summary of Changes

Extracted a foundation-grade gherclj step layer so the foundation features
(features/cli/*, foundation subset of features/module/*) route only to
foundation namespaces, and split spec_helper — making the eventual isaac-
foundation cut a pure file move. All work landed in green sub-steps (bb spec +
bb features + gherclj ambiguity green at each commit).

New foundation step namespaces (spec/isaac/foundation/):
- cli-steps — isaac-run wrapper + stdout/stderr/exit + stdin/command/clock +
  Isaac-root-config + isaac-file-contains. The wrapper is foundation-clean via
  three hook registries: register-isaac-run-preflight!/postflight! (LLM/HTTP/
  drive capture) and register-isaac-run-wrapper! (memory-tool clock).
- log-steps — the log has entries/no entries matching:.
- fs-steps — file fixtures/assertions (the file X contains:/exists/does not
  exist, a file X exists with content), all isaac/EDN-file writes (the isaac
  file X exists with:, the (EDN )isaac file ... contains:/exists with:, the
  isaac file X does not exist), and the session file write (the file X exists
  with:). Config-change notify is a post-write hook.
- root-steps — an (empty) Isaac root at, an empty Isaac state directory, a
  module manifest ...:. initialize-root! does the foundation reset + runs a
  root-setup hook.

Seams (server layer registers; foundation stays server-free):
- server_steps registers the post-write config-change hook (runtime/notify-path!).
- session_steps registers the root-setup teardown hook (grover/drive/bridge-
  cancel/comm-registry resets, remove-ns telly, tool-registry/single-turn
  clears, memory-store install + sidecar alter-var-root).
- config_steps made foundation-clean in place (app/current-config -> config/snapshot,
  dropped isaac.server.app).

Server delegation + ambiguity: server/cli/cli_steps, server_steps, service_steps,
tools_steps, session_steps drop the moved steps + routing (keeping server-only
steps) and require + delegate to the foundation layer. gherclj ambiguity clean
(no phrase registered twice). Dead helpers removed from each.

spec_helper split: foundation isaac.spec-helper (logs/config/await, requires only
config.api) vs new spec/isaac/session/spec_helper.clj (store helpers); 16 requirers
re-pointed. Foundation specs (main_spec, module/loader_spec) dropped stray server
requires. Spec splits accompanied the moved steps (foundation/cli_steps_spec,
log_steps_spec, fs_steps_spec; notify-seam test relocated to server_steps_spec).

Checkbox 5 audit (bb gherclj steps -> route every features/{cli,module} phrase to
its ns): 0 strays. 159 foundation step lines route to isaac.foundation.* /
config_steps; 28 are the genuine server-scenario subset (reply/provider/server-
started/comm/service). The one stray found (init.feature's 'a file exists with
content' in tools_steps) was moved to foundation.

No .feature scenarios were edited — only step namespaces. Final: bb spec 1897,
bb features 744, gherclj ambiguity clean.



## Verification failed

HEAD: aacef2c79d532412a7225400ead92dc93480f2b1
Working tree: clean

### Failed: Acceptance — foundation specs require only foundation namespaces

Bean acceptance: "Foundation features and the foundation specs listed above require only foundation namespaces + the new step layer."

`spec/isaac/main_spec.clj` still has a top-level require of `[isaac.session.store.spi :as store]` (line 10). It is used only to stub `store/register!` in the "installs the active fs into runtime init" test (line 252), but the namespace coupling remains. The bean body and summary both claim main_spec was re-themed to drop stray server requires; `module/loader_spec.clj` is clean, but main_spec is not.

**Fix:** Drop the `isaac.session.store` require; stub via `requiring-resolve` (or similar) inside that one `it` block so main_spec requires only foundation namespaces.

### Passed checks (for worker context)

- `bb spec` — 1897 examples, 0 failures
- `bb features` — 744 examples, 0 failures (clean re-run)
- `bb gherclj ambiguity` — no ambiguous phrases
- Foundation step layer present: `cli-steps`, `log-steps`, `fs-steps`, `root-steps`; server/session/config steps delegate
- `isaac.spec-helper` foundation-only; `session/spec_helper` holds store helpers
- `config_steps` — foundation requires only
- No `features/cli/` or `features/module/` edits in bean commits (5489eb37..aacef2c7)
- Pass A smell scan on foundation/server step specs — clean
- Speed within baseline gates

## Verification fix (main_spec server require)

The isaac.session.store require + store/register! stub had been re-added to
main_spec's "fs-init" test by a concurrent edit (which also added a
register-module-cli-commands! no-op stub), reverting the checkbox-4 removal.

main.clj has zero session.store references, and the test passes without the
stub (the register-module-cli-commands! no-op cuts the only path that could
reach store/register!), so the stub was dead — removed it and the
[isaac.session.store.spi :as store] require entirely (cleaner than the suggested
requiring-resolve, since there is no live coupling). Both foundation specs
(main_spec, module/loader_spec) now require only foundation namespaces;
loader_spec's :isaac.server/comm occurrences are keyword literals in test data,
not requires. bb spec 1897 + bb features 744 green.
