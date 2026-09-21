isaac-b809  todo   bug  [high]  discord, config
Discord JVM spec: token added via config hot-reload never propagates — current-config stays at boot
---

Found 2026-09-21 working isaac-deds (work-2): `bb jvm-spec` in
isaac-discord fails exactly one spec — "Server app — Discord integration
connects Discord gateway when token is added via config hot-reload"
(spec/isaac/server/discord_app_spec.clj:81). **Pre-existing**: CI has
been red on this spec since the isaac-okw1 pin jump (run 35419881083,
2026-09-19) — same failure, same spec, before any deds change.

## Evidence

- The spec boots via `sut/start!` with a `change-source/memory-source`,
  writes `comms.discord.discord/token "new-token"` to the config file,
  calls `change-source/notify-path!`, then awaits the token in
  `(sut/current-config)`.
- Instrumented run: `current-config` still holds the **boot** config
  (`{:comms {:discord {}}}`) — the reload never lands, `connected` stays
  nil. The await times out on the first clause.
- Native `bb spec` (52 examples) skips this spec; only the JVM suite
  (`clojure -M:spec`, 105 examples) runs it — which is why local runs
  looked green while CI was red.

## Suspects

The okw1 pin jump crossed the config-watching handoff: isaac-1pi2 moved
watching to foundation ("foundation owns config watching, and it is on
by default"), isaac-http 8d6ab8f "http stops owning config watching",
and isaac-agent 1d49e49 dropped the agent's copy of the change source.
The spec's memory-source + `notify-path!` flow presumably no longer
reaches the reload path the server app uses.

## Work

Reproduce with `bb jvm-spec spec/isaac/server/discord_app_spec.clj` in
isaac-discord. Trace the notify → watcher → reload → current-config
chain against foundation's watching seam; fix the spec harness or the
seam, whichever is wrong. Acceptance: that spec passes in the JVM suite
and isaac-discord CI goes green on main.
