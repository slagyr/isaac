---
# isaac-5qc
title: "Show compaction activity in ACP interface"
status: completed
type: feature
priority: low
created_at: 2026-04-16T05:40:19Z
updated_at: 2026-04-16T16:25:36Z
---

## Description

When a session compacts, the ACP client (IDEA) should show feedback — e.g. a status notification or agent_message_chunk indicating compaction is happening. Currently compaction is invisible to the user in the ACP flow.

## Design

Add agent_message_chunk notification with text 'compacting...' to the ACP channel during compaction. The notification is sent via the channel's text-update mechanism before compact! runs. Feature file: features/acp/compaction_notification.feature

