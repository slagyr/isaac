Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-discord** to native bb specs via the shared runner.

## Wrinkle
Its tasks compute an alias from a `dev-local?` switch (`:dev-local:spec` vs `:spec`). Preserve that behavior — the native path must still honor the dev-local dep set (e.g. env/flag-gated deps) so local vs CI runs stay equivalent.

## Acceptance
- [x] `bb spec` / `bb features` native where possible (no `clojure -M` for the native subset), streamed; dev-local behavior preserved on `jvm-*`.
- [x] PARITY: full suite native+jvm == JVM results; JVM-only specs routed to `jvm-*`.
- [x] `bb ci` uses the native path (native smoke + full JVM suite); before/after wall-clock recorded here.

## Implementation (main @ 49cf800)

- **foundation-test-support pin:** `43cf46e00087bf066a9e065ccc3d48dd2814ac23` (x5ru main).
- **No** local `test_tasks.clj` copy.
- **dev-local switch:** preserved on `jvm-spec` / `jvm-features` / `spec-jvm`. Predicate unchanged: `(and (not (System/getenv "ISAAC_GIT")) (exists? "../isaac-agent/src/isaac/session/frequencies.clj"))` → `:dev-local:<suite>` else `:<suite>`. CI sets `ISAAC_GIT=1`.
- **Native `bb spec`:** `discord_spec.clj` + `rest_spec.clj` (37 examples, ~0.7s). Subprocess-wrapped so speclj `System/exit 0` does not unwind the task.
- **JVM-only (documented, not dropped):**
  - `module_activation_spec.clj`, `service_spec.clj`, `discord_app_spec.clj` — require `isaac.server.app` → `clout` → `instaparse` (`Protocol not found: clojure.lang.IHashEq` under SCI).
  - `gateway_spec.clj` — scheduler delay-trigger schema (`missing lex :present-when? in :validations`) under SCI; 13 failures if forced native.
  - All features — load server.app/clout; `bb features` → `jvm-features`.
- **`bb ci`:** `config-bypass-lint` + native `bb spec` + `bb jvm-spec` (full 83) + `bb jvm-features` (53).

## Wall-clock

| | wall (s) | notes |
|---|---:|---|
| before (JVM-only `bb ci`) | 55.27 | 83 specs + 53 features |
| after (`bb ci` native+JVM) | 60.67 | +native smoke 37ex/0.7s; full JVM suite still green |

Native unit smoke is the win for tight loops (`bb spec` ~0.7s vs ~12s JVM). Full gate cost is comparable (extra native pass).
