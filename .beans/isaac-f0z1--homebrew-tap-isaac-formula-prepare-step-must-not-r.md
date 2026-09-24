---
# isaac-f0z1
title: 'homebrew-tap isaac formula: prepare step must not read the user''s ~/.m2/settings.xml (sandbox EPERM breaks the build on any machine that has one)'
status: todo
type: bug
priority: high
created_at: 2026-09-24T16:42:26Z
updated_at: 2026-09-24T16:42:26Z
---

Micah, 2026-09-24, on his laptop: `brew upgrade --fetch-HEAD isaac` failed in the formula's `bb … prepare` step with `Error building classpath. /Users/micahmartin/.m2/settings.xml (Operation not permitted)`. The file is an ordinary readable Maven settings file. Homebrew's build sandbox denies reads under the user's home, and tools.deps (via deps.clj) reads `~/.m2/settings.xml` from Java's `user.home` whenever it exists — the formula's `deps_home` only redirects `CLJ_CONFIG`/`DEPS_CLJ_DIR`, not `user.home`. Zanebot has no settings.xml, so the same build passed there.

## Fix (slagyr/homebrew-tap, Formula/isaac.rb)
- Run the prepare step with the JVM's home pointed at `deps_home`, e.g. `ENV["JAVA_TOOL_OPTIONS"] = "-Duser.home=#{deps_home}"` (or the deps.clj-specific env the tool honours) for the `bb … prepare` system call, and set the same in the generated `bin/isaac` wrapper only if runtime classpath resolution also consults settings.xml (check `isaac modules install` on a host with one).
- Test: build on a machine with a `~/.m2/settings.xml` present (`brew install --HEAD isaac` from a clean state) — must succeed without moving the file.
- Runbook note in isaac-foundation README (install section): none needed once fixed; until then, move `~/.m2/settings.xml` aside for the build.

Repo scope: slagyr/homebrew-tap (formula). Not an isaac-* module.
