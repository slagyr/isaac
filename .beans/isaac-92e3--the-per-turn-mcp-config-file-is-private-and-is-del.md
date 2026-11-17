---
# isaac-92e3
title: The per-turn MCP config file is private and is deleted when the turn ends
status: completed
type: bug
priority: normal
created_at: 2026-11-17T17:57:14Z
updated_at: 2026-11-17T18:13:30Z
---

Found by Zane Lab, 2026-11-16 (security finding
`mcp-config-tempfiles-never-deleted`, low; experiment `mcp-tempfile-cleanup`).
Repo: **isaac-claude-code** (`api/claude_cli.clj`).

## What is wrong

Every driven Claude turn writes a config file telling the `claude` CLI how
to reach Isaac's tools: `write-mcp-config!` uses
`File/createTempFile "isaac-mcp-" ".json"` straight in the temp directory,
mode 0644, holding the turn's loopback URL and a bearer nonce. Nothing
deletes it. `cleanup-mcp-turn!` (the `finally` in `invoke!`) stops the
listener and clears the registry but leaves the file. zanebot's temp
directory held 17,403 of them on 2026-11-16, 4,107 written that day. Spec
runs leak them too (35 per `claude_driver_spec` run).

The nonce is dead once the turn ends, so the old files are clutter. While a
turn runs, the file gives any process of the same user that session's tools.

Also: if writing the file fails after the listener has started, the listener
and its nonce keep running until the process exits.

## Acceptance

```gherkin
Scenario: the config file is private while the turn runs
  Given a driven Claude turn is running
  Then its MCP config file is mode rw------- in a directory of mode rwx------
  And that directory is not the shared temp directory itself

Scenario: the file is gone when the turn ends
  When a driven Claude turn ends by reply, by error, by an exception or by a cancel
  Then its MCP config file no longer exists

Scenario: a failed write leaves nothing behind
  Given writing the MCP config file fails
  When a driven Claude turn starts
  Then no listener is left running for that turn
  And no file is left

Scenario: a clean server stop removes files of turns still running
  Given two driven Claude turns are running
  When the server process is stopped with SIGTERM
  Then neither MCP config file exists
```

- The stop-time cleanup must run in the live server. The lab's first patch
  hung it on the module's `on-unload`, which never fires there (the module
  is activated lazily); a raw `Runtime/addShutdownHook` is refused by
  `bb ci`'s `lint-cli-host`. Use the process host's shutdown seam and prove
  it with a real subprocess, as the reviewer's `kill-proof` does.
- No `deleteOnExit` (its registry grows by one path per turn in a
  long-running server) and no startup sweep of the shared temp directory
  (live, sandbox and spec processes share it).
- Works with isaac-55br and isaac-daom (the instant Stop): a stopped turn's
  file is deleted even though the turn returns before `claude` has exited.
- A spec run leaves no `isaac-mcp-*` files behind.

## Exceptions

Authorized by the planner, 2026-11-17: no existing feature scenario changes.
New scenarios or steps that prove the acceptance may be added.

## Working notes

- Approved by Micah 2026-11-17. Ungated: worker, then verifier. The
  scenarios above are the behaviour to prove; specs are fine where the
  feature steps cannot express file modes or a real subprocess, and say
  which is which.
- Reference work on zanebot (read-only):
  `/Users/zane/agents/zane-lab/experiments/mcp-tempfile-cleanup/`
  (`fix.v2.patch`, `REVIEW.md`, `kill-proof.clj`). The patch was cut against
  `836ee0da`; main has moved a long way (streaming, per-turn cancel, instant
  Stop). Take the idea and the specs and write the change against today's
  main. You own the result.
- Do not run the lab's `check.sh` or `kill-proof.sh` as they are. Never
  signal a process group, and never use `pkill` or `killall`; stop a
  subprocess by its own pid.
- `bb ci` and `bb jvm-spec` pass, and `CI Tests` is green on the bean
  branch (Linux) before handoff. No module version bump.
- Not in this bean: deleting the leftovers already on zanebot. The planner
  does that after deploy.

## Implementation notes (2026-11-17)

- `isaac-claude-code` branch `bean/isaac-92e3`, commit `221e2a6` (preceded by `537ca13`, `58cffd3`). Owner-only 0600 per-turn file in 0700 per-process directory; listener and partial file cleaned if writing fails; turn `finally` removes the file on reply, error, exception or cancel. One shutdown callback on `host/process-host` cleans in-flight files and directory even if the initiating turn is bound to an embedded host. No module on-unload dependency, `deleteOnExit`, shared-directory sweep or version bump.
- Acceptance proven in `claude_driver_spec.clj`: mode and location, four turn outcomes, failed write and listener, process-host binding; real JVM subprocess `test-resources/mcp_shutdown.clj` creates two live configs, receives SIGTERM by its own process handle, then checks both files and directory are gone. These OS permissions and process-signal assertions are specs, not feature steps. No feature scenarios changed.
- `bb ci`: 119 examples/0 failures (3 opt-in pending), features 76/0; `bb jvm-spec`: 72/0 (3 pending). CI Tests Linux workflow_dispatch on `bean/isaac-92e3` run 44144218012: success. `bb bean-gate verify isaac-92e3`: exit 2, no feature-baseline; verifier path.



## Landed on main (2026-11-17)

main-sha: isaac-claude-code afd7c4835bd2b2b7ae465f597d0823c2f6e62aa3

Verified by perceptor@verify-isaac-92e3: clean review worktree at 221e2a6; bb ci 119 specs/0 failures (3 opt-in pending), 76 features/0 failures; bb jvm-spec 72 specs/0 failures (3 opt-in pending); CI Tests Linux run 44144218012 success. No feature edits; private file and directory permissions, reply/error/exception/cancel deletion, failed-write listener cleanup, process-host SIGTERM subprocess test reviewed. Existing suite logs and three opt-in pending are pre-existing. No .verify-baseline.edn is present; no speed baseline was evaluated.
