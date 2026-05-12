---
# isaac-m703
title: "Fix delivery queue isaac file assertions"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T19:15:35Z
updated_at: 2026-04-23T19:53:06Z
---

## Description

`bb features` currently fails in `features/delivery/queue.feature` after the state-file -> isaac-file rename. The affected scenarios are: successful delivery is removed from the queue; transient failure reschedules the delivery with backoff; delivery moves to failed after max attempts. Investigate whether the renamed EDN isaac file assertions are resolving the wrong paths or whether delivery worker state writes are now diverging from the feature expectations, and restore the approved behavior.

