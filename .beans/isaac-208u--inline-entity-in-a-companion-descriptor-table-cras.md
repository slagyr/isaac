---
# isaac-208u
title: Inline entity in a companion-descriptor table crashes config load
status: in-progress
type: bug
priority: critical
created_at: 2026-10-01T02:19:26Z
updated_at: 2026-10-01T02:23:51Z
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
