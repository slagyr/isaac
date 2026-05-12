---
# isaac-0hh
title: "WebSocket handler must flush tool notifications in real time"
status: completed
type: bug
priority: high
created_at: 2026-04-13T05:24:56Z
updated_at: 2026-04-13T05:32:56Z
---

## Description

## Problem

Tool notifications through the ACP WebSocket are batched. The server-side handler in `src/isaac/server/acp_websocket.clj:on-receive!` creates a StringWriter, passes it as the output-writer, then reads all written lines AFTER dispatch completes. Tool notifications land in the StringWriter during the turn but only reach the WebSocket after the entire turn is done.

This means Toad shows tool calls and the final response at the same time instead of streaming tool activity in real time.

The local `isaac acp` path was fixed by isaac-8vt (channel writes directly to stdout). The WebSocket path was missed.

## Root cause

```clj
(defn- on-receive! [opts request channel line]
  (let [writer (java.io.StringWriter.)                    ;; ← buffer
        result (dispatch-line (assoc opts :output-writer writer) line)]
    (doseq [message-line (ws/written-lines writer)]       ;; ← flushed after turn
      (send-line! request channel message-line))
    (send-dispatch-result! ... result)))
```

## Fix

Replace the StringWriter with a writer that sends to the WebSocket immediately:

```clj
(defn- ws-writer [request channel]
  (proxy [java.io.Writer] []
    (write [s] (send-line! request channel (str s)))
    (flush [])
    (close [])))
```

Tool notifications from the ACP channel will flow through the WebSocket as they're emitted, not buffered until turn end.

## New step definitions needed
- `the loopback holds the final response` — blocks the final response in the loopback queue
- `the loopback releases the final response` — unblocks it
- `the output eventually contains {pattern}` — polls output from background proxy
- `the output does not contain {pattern}` — snapshot assertion on current output

## Acceptance

- `bb features features/acp/proxy.feature` passes with @wip removed from the streaming scenario
- @wip removed
- Manual: Toad shows tool calls as they happen during remote sessions, not all at once

## Acceptance Criteria

@wip streaming scenario passes. Toad shows real-time tool activity over WebSocket.

