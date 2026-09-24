---
# isaac-clba
title: comm delivery audit log drops the failure reason and logs a target it never reads
status: completed
type: bug
priority: normal
created_at: 2026-09-24T16:45:41Z
updated_at: 2026-09-24T18:28:54Z
---

## Problem

`isaac.comm.delivery.worker/audit-fields` is the only thing the worker logs
about a failed delivery:

```clojure
(defn- audit-fields [record]
  {:id     (:id record)
   :comm   (:comm record)
   :target (:target record)})
```

Two defects, both hit while diagnosing a dead delivery on yopp 2026-09-24:

1. **The error is dropped.** `send!` returns `{:ok false :transient? ... :error
   <text>}` and the worker throws `:error` away. Five `:comm.delivery/
   attempt-failed` lines and a `:comm.delivery/dead-lettered` line said only
   that something failed. Establishing *why* took reproducing the JSON-RPC
   call by hand against the live host — the log itself was not diagnostic.

2. **`:target` is a key no comm sets.** iMessage carries the recipient as
   `:imessage/target`; the generic `:target` is absent, so every line logs
   `:target nil`. That is worse than omitting it: it reads as "the record has
   no recipient", which is a live and misleading hypothesis when a delivery is
   failing. It cost a detour here.

## Acceptance

- `attempt-failed` / `dead-lettered` / `deferred` carry the `:error` the comm
  returned (whatever its type — string or keyword such as `:timeout`).
- The recipient logged is the one the record actually carries; a record with
  no generic `:target` omits the key rather than logging `nil`.
- Scenarios assert the error text reaches the log on both a transient failure
  and the terminal dead-letter.

## Exceptions

- No change to retry or dead-letter *policy* — that is isaac-fkjq.
