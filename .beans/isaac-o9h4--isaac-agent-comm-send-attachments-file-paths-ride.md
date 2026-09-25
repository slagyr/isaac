---
# isaac-o9h4
title: 'isaac-agent: comm__send attachments — file paths ride the delivery record; comms opt in with :send-attachments?'
status: todo
type: feature
priority: high
created_at: 2026-09-25T03:37:51Z
updated_at: 2026-09-25T03:40:52Z
---

## Why (Micah, 2026-09-25)

"We will want to send attachments in emails and gchat messages." comm__send
carries a string body plus scalar comm fields; nothing in the tool, the
delivery record or the worker knows about files. This bean adds the generic
half; isaac-gchat and isaac-gmail beans add the uploads.

## Design (agent stays generic — no comm knowledge)

- comm__send parameters gain `attachments`: array of strings, optional —
  "Local file paths to attach. Only comms that accept attachments take
  them." Always present in the schema (like comm/content).
- Manifest `:isaac.agent/comm` entries may declare `:send-attachments? true`.
  The tool refuses `attachments` for a comm that does not (error names the
  comm: "comm gchat does not accept attachments"), and queues nothing.
- Each path must exist, be a regular file, and lie inside the turn's
  allowed directories (the same check the fs tools use); otherwise the tool
  errors naming the path, queues nothing.
- The queued delivery record carries `:attachments ["<path>" …]` verbatim;
  the delivery worker passes it through to the comm's `send!`. Retry /
  dead-letter unchanged.
- The `telly` fixture comm declares `:send-attachments? true` for the
  feature; `skybeam` does not.

## Acceptance (features/tool/comm_send.feature — baselined)

- [ ] "comm_send offers attachments alongside the common fields (isaac-o9h4)".
- [ ] "attachments ride the queued delivery for a comm that accepts them (isaac-o9h4)".
- [ ] "a comm that does not accept attachments refuses the call and queues
  nothing (isaac-o9h4)".
- [ ] "an attachment outside the allowed directories is refused (isaac-o9h4)".
- [ ] Version bump; manifest schema for `:send-attachments?`; bb spec /
  bb features / bb lint green; bb jvm-spec if any protocol changes.

Likely repo scope: isaac-agent (tool/comm_send.clj, comm manifest schema,
telly fixture, comm_send.feature).

feature-baseline: isaac-agent ee969478c6d14cc2ba7578fa9eb96386e7a5ffc1
feature-blob: isaac-agent features/tool/comm_send.feature dcf7e6996c469bfd743e41d12a85ed1c864287e8 84,96,116,131
