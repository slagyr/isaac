---
# isaac-mdv0
title: 'Foundation pre-work: extract foundation-grade gherclj steps and split spec_helper'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:50:37Z
updated_at: 2026-06-12T15:18:01Z
parent: isaac-brth
---

Phase A step 12 of the isaac-foundation extraction (see isaac-brth reshaping
note). Foundation features (features/cli/*, features/module/* machinery
subset) currently use steps defined in server-flavored namespaces. Extract a
foundation-grade steps layer so those features can move at cut time; server
steps require + delegate (gherclj reports ambiguity if both repos later define
the same phrase).

- [ ] New foundation step namespaces (matching the isaac.**-steps selector,
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
- [ ] Server step namespaces require + delegate instead of redefining; run the
      gherclj ambiguity check.
- [x] Split src/isaac/spec_helper.clj: foundation scaffolding vs server store
      helpers (it currently requires isaac.session.store*).
- [x] Re-theme stray server requires in foundation specs so they can move
      later: main_spec requires isaac.session.store; module/loader_spec
      requires isaac.server.routes, isaac.comm.registry, isaac.hooks (use
      marigold berths instead).
- [ ] Per-feature audit of features/{cli,module}/* for stray server-step usage
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
