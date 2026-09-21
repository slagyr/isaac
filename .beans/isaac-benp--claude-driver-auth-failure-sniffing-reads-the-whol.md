---
# isaac-benp
title: 'claude driver: auth-failure sniffing reads the whole stream — a transcript that says ''unauthorized'' parks the session and posts a 1.7 MB notice'
status: draft
type: bug
priority: high
tags:
    - provider
    - claude-code
created_at: 2026-09-21T18:27:14Z
updated_at: 2026-09-21T18:27:14Z
---

Repo: **isaac-claude-code** (`src/isaac/llm/api/claude_cli.clj`). Field
2026-09-21 18:17Z, session isaac-work-2 (the isaac-q1iu turn): a Discord
attention notice "Provider tono-claude is broken … session isaac-work-2"
whose body was the driver's entire stream-json stdout — 1.7 MB, starting
with the `init` event — and the turn was parked as `:auth` weather although
the CLI exited 0 with a normal `result` event and the provider was fine
(the next run 26 s later was clean).

## Why

Two lines in the same region:

```clojure
(def ^:private auth-failure-re
  #"(?i)not logged in|please run\s*/login|invalid api key|not authenticated|no credentials|unauthorized")

(defn- auth-failure? [out err]
  (boolean (re-find auth-failure-re (str (or out "") "\n" (or err "")))))

(defn- error-message [result]
  (or (not-empty (str/trim (str (:err result))))
      (not-empty (str/trim (str (:out result))))
      "claude binary failed"))
```

`failed?` treats a zero-exit run as failed when `auth-failure?` matches —
and it matches against the **whole stdout**, which is the model's own
transcript. The q1iu worker was writing OIDC scenarios about 401
`Unauthorized`; the word appeared in its tool calls and the driver decided
the CLI was not logged in. Then `error-message` handed the whole stream to
`:message`, `dispatch` posted it as the provider-broken notice, and the
wall classifier parked the session on `:auth`. Any session that mentions
"unauthorized" or "invalid api key" in its work trips this.

## Fix

- Auth-failure detection reads the CLI's own signal: the `result` event's
  `is_error` / error subtype, and stderr — never the assistant/tool text
  in the stream. If the CLI reports login trouble it says so in the
  `result` event (or exits non-zero with stderr); keep the regex only for
  stderr and for a `result` event's error text.
- `error-message` for a run with a parsed `result` event is that event's
  error text; for a run with no result, stderr, then a bounded excerpt of
  stdout (the last N chars, or the last event), never the whole stream.
  Attention notices are for humans on Discord.
- `:reason :auth` only when the signal above says auth.

## Scenarios (isaac-claude-code features, the fake-CLI harness)

- a zero-exit run whose assistant text contains "401 Unauthorized" completes
  normally — no fallback, no attention, no park
- a run whose `result` event is `is_error` with an auth message parks with
  `:reason :auth` and the attention message is that error text
- a run with no `result` event and 2 MB of stream posts an attention
  message under 2 KB that ends with the stream's last event

Unrelated but seen in the same incident: after cancel/restart the drive
re-bound the parked hail to a stale session — that is isaac-3wiu / isaac-6doh.
