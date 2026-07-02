---
# isaac-3692
title: 'Neutral log-stream registry: isaac logs discovers streams via a berth'
status: completed
type: feature
priority: normal
created_at: 2026-06-30T00:52:15Z
updated_at: 2026-07-02T17:34:04Z
---

isaac logs lives in foundation but currently hardcodes log file names (isaac.log; 9e52ea8 defaults it to cli.log; and a server.log default would be just as wrong). "server" is a SERVER-MODULE concept foundation shouldn't know. The logs command needs a NEUTRAL way to view any registered log stream.

## Design (settled with Micah 2026-06-29)
- **Log-stream berth** (e.g. :isaac/log-stream): each producer DECLARES its stream(s) in its manifest -> {<name> {:file "logs/<name>.log" :description "..."}}. The server module contributes :server (logs/server.log); foundation contributes :cli (logs/cli.log, CLI is foundation-level). Foundation aggregates the registry. New modules with their own log just register a stream and appear automatically.
- **isaac logs UX:**
  - `isaac logs <name>`  -> tail that stream
  - `isaac logs --list`  -> list registered streams (name, file, description)
  - `isaac logs` (no name) -> LIST the registered streams (NO default-stream config, NO :primary flag — Micah chose option c). User picks from the list.
  - -f / -n / formatting work on whichever stream is selected.
- **Foundation stays neutral:** the logs command resolves paths from the registry + :logging config; remove all hardcoded isaac.log / cli.log / server.log from foundation's logs command.

## Supersedes
The "default isaac logs to server.log" quick-fix and 9e52ea8's cli.log default — both hardcode a module-owned stream in foundation. This replaces them with discovery.

## Related
isaac-tqm1 (writes server.log), isaac-k9b7 (writes cli.log), isaac-f0fq (berth :output). The server/cli already WRITE their files; this adds the registry + neutral viewer.



## Verification failed

HEAD: 41cde018c57541d2273c55d0822b18f12c271e94
Working tree: clean

Verification blocked: the handoff contains no implementation to review. `git show --name-only HEAD` shows the handoff commit changed only `.beans/isaac-3692--neutral-log-stream-registry-isaac-logs-discovers-s.md`; no source, spec, feature, `bb.edn`, or `deps.edn` files were changed for this bean. Repository scan from the verify checkout also found no `src/`, `spec/`, or `features/` directories in this repo state, so there is no shipped code or acceptance test artifact here to validate against the bean design.

The incoming verify hail is also missing the worker handoff payload required by the verify skill (`repo`, `summary`, `what-done`, `commit`, `submitter-crew`, `submitter-session`, `thread_id`), so exact return-to-worker routing is unavailable.

Please hand off actual implementation evidence: the code/test commit(s), what changed, and the originating worker session. If this bean was sent to the wrong repo/session, re-route it with the correct context.


---

## Implementation evidence (recovered from a stranded local worker note; status left in-progress)

The verify-fail above blocked on a handoff that carried no code evidence. The
implementation *was* done in the sibling repos — these worker notes were stranded
uncommitted in a work-1 checkout and are salvaged here so the re-verify has the
commits/what-changed it was missing. SHAs are as recorded by the worker and
should be re-fetched/confirmed at verify time.

**isaac-foundation** (main 04d6dbb, via ab256f0 → 3412837 → 04d6dbb):
- New `:isaac/log-stream` berth (map of name -> {:file :description}) with a
  per-entry factory `isaac.logs.streams/register-stream!` merging each
  contribution into a nexus-held registry. Foundation contributes `:cli`
  (logs/cli.log); modules register their own stream and appear automatically.
- `isaac logs` rewritten (isaac.logs.cli): `logs <name>` tails that stream's
  file; bare `logs` / `--list` lists registered streams (no default stream);
  `--file` still views a path directly. Removed the hardcoded config-log-path /
  cli-log-path defaulting — foundation hardcodes no names.
- standard-run-fn kept intact (shared by isaac-server); the logs run-fn injects
  positional `:arguments` itself.
- Hardened the logger: file logging is now best-effort (save-entry wraps the
  write in try/catch) so an unwritable log path never crashes the caller.
- Dropped @wip from features/logs/streams.feature; removed two superseded legacy
  `:log {:file/:output}` scenarios from cli.feature.

**isaac-server** (main e9e51ab):
- Contributes `:isaac/log-stream {:server logs/server.log}` so `isaac logs
  server` / `--list` see it.
- Bumped the isaac-foundation pin (main + both marigold subprojects) to 04d6dbb
  so the berth exists to validate against.

**19 server feature failures — root-caused and fixed.** They pre-dated this work
(red on server origin/main): the server drives logging from :logging.output
(default :file), so boot-time apply-server! switched the test harness's :memory
output to :file — silencing the in-memory buffer the log-assertion features read.
Fix (isaac-foundation apply-server!, ~8 lines): when output is :memory, still
configure the durable server sink (so logs/server.log is written for the
file-lifecycle features) but PRESERVE :memory — save-entry already mirrors each
entry to the in-memory buffer while output is :memory, so both memory-assertion
and file-lifecycle features pass. Production boots :stderr, unaffected.

**Verification recorded by the worker (CI-faithful, git pins):**
- isaac-foundation 9b8ac71: spec 807/8 (8 pre-existing module-lifecycle/protocol
  flakes, identical on baseline), features 124/0.
- isaac-server 59960da (foundation pin 9b8ac71): spec 159/0, features 55/0.

**Verifier caveat to re-check:** the streams.feature "Foundation stays neutral"
scenario is green in CI but fragile — it fails LOCALLY when a sibling
../isaac-server checkout carrying the :server stream is on the JVM classpath
(modules_install add-libs makes the server manifest discoverable). Worth
hardening against classpath-contributed streams in a follow-up.
