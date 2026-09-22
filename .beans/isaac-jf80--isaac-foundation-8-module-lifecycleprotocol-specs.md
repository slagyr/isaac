---
# isaac-jf80
title: 'isaac-foundation: 8 module lifecycle/protocol specs fail under the JVM runner only'
status: todo
type: bug
priority: normal
created_at: 2026-09-22T22:05:28Z
updated_at: 2026-09-22T22:05:28Z
---

Repo: **isaac-foundation**.

`bb jvm-spec` fails 8 examples that are green under `bb spec` (native
babashka):

```
1-6) module lifecycle (topological load/unload, rollback, reconcile, idempotent load)
7-8) isaac.module.protocol treats missing lifecycle hooks as no-ops
```

Root symptom: `module load failed for marigold.bridge`, caused by
`NullPointerException: Cannot invoke "clojure.lang.IAtom.swap(…)" because
"atom" is null` (`isaac.module.lifecycle:278`).

Reproduced on `origin/main` (eaae014) — identical 8 failures. Pre-existing;
found while checking isaac-ruom's "bb jvm-spec green" bar.

## Done when

- `bb jvm-spec` is green in isaac-foundation, or the JVM-only divergence is
  understood and the specs run correctly on both runners
