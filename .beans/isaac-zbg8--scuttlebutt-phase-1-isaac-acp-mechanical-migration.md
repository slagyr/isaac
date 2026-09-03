---
# isaac-zbg8
title: 'Scuttlebutt phase 1: isaac-acp mechanical migration to the new Comm protocol'
status: draft
type: task
tags:
    - scuttlebutt
    - acp
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-03T16:40:55Z
blocked_by:
    - isaac-jarr
---

Split from isaac-i5ps so the pin bump can ride the scuttlebutt train without the rendering redesign. Mechanical only: extend+defaults; on-chatter → agent_message_chunk exactly as today's on-text-chunk; on-reply → same final message path; tool events unchanged; compaction on-bulletin → today's session/update compaction notification (isaac-5qc scenario migrates to bulletin rows). No agent_thought_chunk yet — reckoning/aside rendering (option B, 🧠/💬) stays in isaac-i5ps, which becomes blocked by this bean. Bump agent pin to the released 5nxf SHA; features/comm/acp green.
