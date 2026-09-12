
## Worker checkpoint (scrapper@isaac-work-3, 2026-09-12)

Done: reproduced target queue feature isolated green 5/5 (5 examples, 0 failures, 20 assertions each) and a full JVM baseline red at 751/20/1. Added RED/GREEN coverage for Grover reset releasing all wait gates, atomic concurrent scripted dequeue, turn-worker wake coalescing, and teardown refusing to discard an unrealized turn. Focused Grover/session-step/worker specs are green (97 examples, 0 failures, 203 assertions combined). Affected cli, parallel batch, and compaction logging features are green in the latest focused run.

Current red: `features/session/compaction_memory_flush.feature:42` reproduced isolated (3 examples, 1 failure): the filesystem assertion runs before the async compaction future persists `memory__write`. This is the next fixture ordering fix; full acceptance has not run.

Next: add a failing session-step spec proving file/transcript assertions await the current session's async compaction, then fix the assertion seam and rerun the affected family. Resume at `spec/isaac/session/session_steps_spec.clj:178` and `spec/isaac/session/session_steps.clj:1359`.
