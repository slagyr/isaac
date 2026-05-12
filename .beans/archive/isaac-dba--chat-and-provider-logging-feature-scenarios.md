---
# isaac-dba
title: "Chat and provider logging feature scenarios"
status: scrapped
type: feature
priority: high
created_at: 2026-04-08T21:41:55Z
updated_at: 2026-04-08T21:56:43Z
---

## Description

Create features/chat/logging.feature with scenarios that verify structured log entries are produced during chat interactions.\n\n## Scope\n- Scenario: successful chat request logs :chat/request and :chat/message-stored events\n- Scenario: provider error logs :chat/process-error at :error level\n- Scenario: streaming request logs :chat/stream-request and :chat/stream-response events\n- Add any step definitions needed to the chat steps file\n\n## Why\nIsaac-ty6 calls for feature-level coverage of chat/provider logging. The unit specs in chat_spec.clj cover dispatch logging, but there is no feature contract that verifies log events are produced end-to-end during a session interaction.

