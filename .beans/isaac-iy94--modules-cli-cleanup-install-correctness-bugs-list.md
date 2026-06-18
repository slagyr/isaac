---
# isaac-iy94
title: 'modules CLI cleanup: install correctness bugs + list display polish + default-to-list'
status: todo
type: bug
created_at: 2026-06-18T18:23:22Z
updated_at: 2026-06-18T18:23:22Z
---

`isaac modules install isaac.server isaac.agent` exposed a pileup. Observed:

  Error building classpath. Coord of unknown type: {:server #:git{:url "...isaac-server.git", :tag "v0.1.0", :sha "..."}}
  Installed isaac.server

## P1 — install correctness bugs

1. SILENT MULTI-ARG DROP. run-install reads only (first arguments)
   (src/isaac/modules/cli.clj:161); `install A B` installs A, ignores B with no
   error. Decide: install ALL names, or reject >1 with a clear error.

2. DOTTED MODULE ID CORRUPTS CONFIG (root cause of the error above). install
   writes via a per-key dotted path module-config-path -> modules["isaac.server"]
   (cli.clj:146-147). The mutate/nav path splits on the '.', so config becomes
   {:modules {:isaac {:server <coord>}}} instead of {:modules {:isaac.server
   <coord>}}. The loader then reads id :isaac with bogus coord {:server <coord>}
   -> "Coord of unknown type". FIX: write the whole map like remove does
   (cli.clj:196): mutate "modules" (assoc modules id coord). install and remove
   currently use inconsistent strategies; unify on the whole-map rewrite.

3. CONFIG-ONLY OP BUILDS A CLASSPATH. Per isaac-dhzy, install/list never run
   module code, yet "Error building classpath" surfaces. Startup module-CLI
   registration (main.clj:99 register-module-cli-commands! / the maybe-load
   discover!+reconcile) resolves a classpath over :modules on EVERY invocation
   and hard-fails on a bad/unresolved entry. A config edit must not depend on
   classpath resolution, and registration must degrade gracefully (skip/flag a
   bad entry, not abort with a raw error).

4. ERROR + SUCCESS TOGETHER. The run prints the classpath Error AND "Installed
   isaac.server", almost certainly exit 0. Mixed signal; the user can't tell if
   it worked. Resolve once 2 & 3 are fixed; ensure exit code reflects reality.

5. NO VALIDATION ON WRITE. mutate-modules! sets :skip-ref-validation? true and
   :skip-module-validation? true (cli.clj:152-153), so a malformed coord
   persists and poisons every later startup (the classpath error recurs).
   Validate coord SHAPE before writing (the registry coord should be trusted,
   but the write path must be correct — see 2).

## P2 — list display polish

6. ID shows leading ':' (keyword). render-installed-table ID column has no
   :format (cli.clj:81); the module-id-str helper (cli.clj:139-144) already
   strips it but isn't wired in. Add :format to ID in installed + catalog
   tables. Keep --edn/--json emitting the keyword (correct for structured out).

7. COORD renders raw EDN namespaced-map (#:git{:url ..., :sha ...}) via pr-str
   (cli.clj:86). Humanize the TABLE (e.g. "git slagyr/isaac-server@d3ffd7f",
   "local ./modules/foo", "mvn group/artifact 1.2.3"); keep the full map in
   --edn/--json.

## P3 — behavior

8. `isaac modules` (no subcommand) should default to `list`. run :else branch
   (cli.clj:241-245) prints help; instead run the `list` subcommand (passing
   through flags like --edn), keeping -h/--help -> usage and unknown subcommand
   -> error. (This is the previously-discussed default-to-list change.)

## Acceptance (feature-test, features/module + features/cli)

• `modules install <name>` writes {:modules {<full-dotted-id> <coord>}} (round-
  trips; `modules list` shows it :ok); dotted ids like :isaac.server work.
• Multi-name install behaves per the chosen decision (all, or clear error).
• No "Error building classpath" during install/list; a bad :modules entry is
  flagged (:invalid), not fatal.
• Table ID has no leading ':'; COORD is human-readable; --edn/--json unchanged.
• Bare `isaac modules` == `isaac modules list`.

## Relationships

• Foundation owns the modules command (isaac-dhzy, done). This is post-ship
  cleanup against real registry use (isaac-xdg3 seeded modules.edn).
