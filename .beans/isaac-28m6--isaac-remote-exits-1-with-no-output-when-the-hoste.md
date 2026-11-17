---
# isaac-28m6
title: isaac remote exits 1 with no output when the hosted command finished with code 0
status: draft
type: bug
priority: normal
created_at: 2026-11-17T00:21:47Z
updated_at: 2026-11-17T00:21:47Z
---

Seen 2026-11-17 00:15–00:18Z. From Micah's Mac, `isaac remote yopp -- dream run --crew yopp` ran 3m40s, printed nothing, and exited 1. yopp's server log: `:cli/command-finished :argv ["dream" "run" "--crew" "yopp"] :code 0 :duration-ms 218820`. The run had succeeded (diary written, digest delivered).

So the client's exit code doesn't match the server's `exit` frame. Suspects, in order:
- the client returns 1 when the socket closes before (or instead of) it reads the `exit` frame, e.g. the server closes right after sending it;
- an idle or read timeout in the proxy during a long silent command (no stdout for ~3.5 min), with the error message swallowed;
- the `exit` frame is sent but not flushed before close.
It also printed nothing on stderr. Whatever the cause, a non-zero exit with no message is a bug of its own.

Repro idea: a hosted fixture command that sleeps a few minutes with no output, then exits 0. Shorter sleeps may pass; find the threshold. Repos: isaac-cli-proxy (client), maybe isaac-cli-server (close ordering).

Needs scenarios before dispatch.
