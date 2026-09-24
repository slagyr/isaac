---
# isaac-fkjq
title: iMessage comm retries a send imsg marked retry_safe false
status: completed
type: bug
priority: high
created_at: 2026-09-24T16:45:29Z
updated_at: 2026-09-24T18:28:53Z
---

## Problem

`isaac.comm.imessage/classify-imsg-error` decides transient-vs-permanent by
regex over the error *message* only:

```clojure
(re-find #"(?i)not authorized|permission|unknown buddy|invalid handle|no such" msg)
```

imsg, however, answers with a structured `:data` map that states the answer
outright. A real reply observed on yopp 2026-09-24:

```json
{"error":{"message":"Delivery outcome unknown","code":-32001,
          "data":{"operation":"send","disposition":"may_have_completed",
                  "transport":"applescript","retry_safe":false,
                  "detail":"Messages automation returned success, but no matching outgoing text row was observed within 8 seconds."}}}
```

`retry_safe: false` with `disposition: "may_have_completed"` means exactly
"do not send this again — it may already have gone out." None of the regex
alternatives match "Delivery outcome unknown", so Isaac classified it
`:transient? true` and the delivery worker retried it **5 times** before
dead-lettering `c972`.

Nothing was duplicated in this instance only because the underlying account
was signed out, so every attempt was a no-op. With a healthy account the same
path sends a human the same message five times.

## Acceptance

- `classify-imsg-error` honours `:retry_safe false` in the error `:data` as
  `{:ok false :transient? false}`, ahead of the message regex.
- `disposition "may_have_completed"` is never retried.
- The regex stays as the fallback for errors that carry no structured `:data`.
- A scenario covers a `-32001` / `retry_safe false` reply: exactly one send
  attempt, then dead-letter.
