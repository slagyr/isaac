---
# isaac-3rxx
title: 'isaac-agent: test-resources/isaac-manifest.edn shadows the real manifest under the JVM spec path'
status: completed
type: bug
priority: normal
created_at: 2026-09-22T22:05:28Z
updated_at: 2026-09-30T14:05:06Z
---

Repo: **isaac-agent**.

`bb spec` (native babashka) and `bb jvm-spec` / `clojure -M:spec` disagree about
which `isaac-manifest.edn` `io/resource` returns. Under the JVM path the
`:spec` alias adds `test-resources` and the fixture manifest there wins, so
`spec/isaac/agent/manifest_spec.clj` reads the fixture instead of
`resources/isaac-manifest.edn` and 4 examples fail:

```
1) isaac-agent declares the comm berth declares :isaac.agent/comm
2) … wires live comms into the comm registry
3) … requires each contribution to name its implementing namespace
4) … carries extra-schema and send-schema slots for composed config
```

Reproduced on `origin/main` (1afd3dc) with
`clojure -M:spec spec/isaac/agent/manifest_spec.clj` → 5 examples, 4 failures.
Pre-existing; not caused by isaac-ruom (found while checking its "bb jvm-spec
green" bar).

## Done when

- the manifest spec reads the shipped manifest on both runners (name the
  fixture something else, or read it from the `resources/` path explicitly)
- `clojure -M:spec` and `bb spec` agree: 0 failures

## Summary of Changes

Landed: isaac-agent spec/isaac/agent/manifest_spec.clj reads resources/isaac-manifest.edn directly instead of io/resource, with a comment on the JVM classpath shadowing. (Triage 2026-09-30, planner, approved by Micah.)
