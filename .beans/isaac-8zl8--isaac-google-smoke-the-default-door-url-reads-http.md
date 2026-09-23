---
# isaac-8zl8
title: 'isaac google smoke: the default door URL reads http.port, which most hosts leave unset — probe the server''s effective port instead'
status: todo
type: bug
priority: normal
tags:
    - google
created_at: 2026-09-23T02:15:07Z
updated_at: 2026-09-23T02:15:07Z
---

## Observed (yopp, 2026-09-23 02:15Z, isaac-google 0.1.10)

`isaac google smoke` reported `FAIL door — door unreachable: java.net.ConnectException` while `curl -X POST http://127.0.0.1:6674/google/pubsub` answered 401 and the Funnel URL answered 401 too. yopp's isaac.edn sets no `:http :port`; `cli/default-door-url` builds the probe URL from that key, so the port was missing or wrong. `--url http://127.0.0.1:6674/google/pubsub` passes.

## Change

Default the probe to the port the server actually listens on: isaac-http's resolved config (its own default when the key is absent), not the raw key. Spec: config with no :http :port → probe URL carries isaac-http's default port; config with a port → that port; --url still wins.

## Acceptance

bb spec / bb ci green in isaac-google; one-time on a host with no :http :port set, `isaac google smoke` passes the door check without --url.
