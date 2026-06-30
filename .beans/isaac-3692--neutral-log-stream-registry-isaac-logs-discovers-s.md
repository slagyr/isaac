---
# isaac-3692
title: 'Neutral log-stream registry: isaac logs discovers streams via a berth'
status: todo
type: feature
priority: normal
created_at: 2026-06-30T00:52:15Z
updated_at: 2026-06-30T19:36:06Z
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
