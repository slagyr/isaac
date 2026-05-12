---
# isaac-e8ls
title: "Add build-prompt to Api protocol with default delegation"
status: completed
type: task
priority: normal
created_at: 2026-05-08T00:05:16Z
updated_at: 2026-05-09T16:01:41Z
---

## Description

Sub-bead 1 of isaac-goq9. Add build-prompt to the Api protocol. Each existing deftype gets a default implementation that delegates to the current builder (prompt/build or anthropic-prompt/build) so no behavior changes. Update build-chat-request in drive/turn.clj to call api/build-prompt instead of the current string-dispatch.

