---
# isaac-208u
title: Inline entity in a companion-descriptor table crashes config load
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-10-01T02:19:26Z
updated_at: 2026-10-01T02:34:54Z
---

Found 2026-09-30 rehearsing the zanebot deploy. With foundation 0c6e881 (isaac-kcck, companion .md load side) or later, any command fails to load a config whose isaac.edn defines an entity INLINE in a table that has a :companion descriptor, e.g. zanebot's

    {:cron {:heartbeat {:crew :zane :expr "0 0 * * *" :prompt "..."}}}

-> java.io.FileNotFoundException: <root>/config (Is a directory), from isaac.foundation.launcher/read-user-config -> config-api/load-resolved.

Repro (foundation alone, no module needed):

    mkdir -p /tmp/cronroot/config
    echo '{:cron {:heartbeat {:crew :zane :expr "0 0 * * *" :prompt "x"}}}' > /tmp/cronroot/config/isaac.edn
    HOME=$(mktemp -d) libexec/isaac --root /tmp/cronroot config validate

95e44ce (deployed on zanebot) is fine; git bisect -> 0c6e881. Blocks the zanebot deploy.

## Acceptance
- Red first: a foundation feature/spec loading a config with an inline entry in a companion-descriptor table (no entity file) loads cleanly; the companion side applies only to entries that have an entity file.
- The repro command prints OK.
- `bb ci`, `bb features-slow`, `bb jvm-spec` green (pre-existing local env failures identical to main).

Ungated (urgent); planner verifies.



## Worker result (2026-09-30)

Root cause: in `companions.clj` (`resolve-cron-prompts`) and `entities.clj` (`resolve-entity-data`), the companion `.md` path was built with `(str root "/" relative)` even when `companion-md-relative` returns nil (no module declares a `:companion` descriptor with an `:entity-dir` for the kind — `:cron` is foundation-builtin, no module needed). A nil `relative` stringified into a bare `"<root>/"` path, which exists as a real directory, so `load-companion-text`'s slurp crashed with `FileNotFoundException "<root>/config (Is a directory)"` instead of just using the inline value.

Fix: only build the companion-md path when `relative` is non-nil (`(when relative (str root "/" relative))`) in both call sites. When there is no entity-dir to resolve, the companion side is skipped entirely and the entry loads from its inline fields, matching pre-isaac-kcck behavior.

Red test: new feature scenario in `features/cli/config_companion.feature` ("a :cron entry inline in isaac.edn loads cleanly with no module declaring :cron (isaac-208u)") — crashed before the fix, passes after.

Repro output after fix:

    $ HOME=$(mktemp -d) libexec/isaac --root /tmp/cronroot config validate
    warning: :cron - unknown key
    OK - config is valid

(exit 0; the "unknown key" warning is pre-existing/expected since no module describes :cron schema here, unrelated to this bug)

bb ci, bb spec, bb features, bb features-slow, bb jvm-spec all compared 1:1 against unmodified main — identical pre-existing failure counts (bb spec 1338/40 both; jvm-spec 1338/49 both; features 364 vs 363 examples — the +1/+2 are our new scenario, 1 pre-existing gitlibs-cache failure on both; features-slow: same pre-existing modules_deps_emit failure on both; bb ci's `pins` lint step fails identically on both branches on a local dev-local schema mismatch, unrelated to this change).

main-sha: d382737eee93141ad55130e07928fdb3d1821bfc
GitHub CI (isaac-foundation, run 36806369539): all 3 jobs green (Slow features, verify/bb ci, Server boot with a module-provided config type).
