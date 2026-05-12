---
# isaac-w17w
title: "exec tool ignores session cwd; defaults to JVM process cwd instead"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T16:11:43Z
updated_at: 2026-04-29T21:37:45Z
---

## Description

The exec tool's workdir defaults to the JVM process cwd (wherever
isaac server was launched from) instead of the calling session's
:cwd field. Read/write/glob/grep already anchor on session cwd;
exec is the outlier.

## Evidence

src/isaac/tool/builtin.clj:531-538

  (defn start-process [args]
    (let [command (get args "command")
          workdir (get args "workdir")
          pb      (doto (ProcessBuilder. ["/bin/sh" "-c" command]) ...)]
      (when workdir
        (.directory pb (io/file workdir)))
      (.start pb)))

When workdir is nil, ProcessBuilder inherits the JVM cwd. Sessions
launched in different directories all execute commands from the same
place. `pwd` returns the same path regardless of session.

## Spec

features/tools/built_in.feature has two new @wip scenarios:
- "Execute defaults workdir to the session's cwd"
- "Explicit workdir overrides the session's cwd"

Both use existing steps; no new step phrases invented.

## Implementation

src/isaac/tool/builtin.clj exec-tool

  - Resolve workdir: (or arg-workdir session-cwd)
  - Lookup session-cwd via storage/get-session keyed by session_key
    (already injected by the runtime per drive/turn.clj:518)
  - Pass the resolved workdir into start-process

Make sure relative workdir args (like "subdir" in the existing
scenario) still resolve correctly — likely relative to whatever
the resolved cwd is.

## Definition of done

- Both @wip scenarios pass
- Existing exec scenarios (echo, exit 1, subdir, timeout) still pass
- bb features and bb spec green

## Related

- isaac-d9am: surfaces cwd in session_info so the LLM can see it.
  This bead is the complementary fix on the exec side.

## Notes

Verification failed: the two new exec cwd scenarios are no longer @wip and features/tools/built_in.feature passes, but the full feature suite still has related regressions, so the bead's Definition of Done is not met. Specifically, bb features fails in exec-driven paths: 1. features/session/llm_interaction.feature 'Model requests a tool call and receives the result' now gets an exec startup error instead of 'hi' (ProcessBuilder tries /bin/sh in directory '/target/test-state'). 2. The fallback tool-call scenario in the same file also fails for the same reason. 3. features/bridge/cancel.feature cancel scenarios fail because the exec tool call does not start successfully, so cancellation never yields the expected cancelled result. These failures appear coupled to the exec cwd change rather than unrelated pre-existing issues, so this bead is reopened until full bb features is green with the new default-cwd behavior.

