---
# isaac-zt4h
title: 'ACP protocol completeness: close the gaps between isaac-acp and the ACP surface'
status: draft
type: epic
priority: normal
created_at: 2026-07-07T19:01:30Z
updated_at: 2026-07-07T19:01:30Z
---


## Context

isaac-acp (0.1.7, pure stdio post-exi2) implements a working core: initialize, session/new, session/load (with full transcript replay), session/prompt, session/cancel; session/update notifications for user/agent/thought chunks, tool_call(+update), available_commands_update. Verified live 2026-07-07. Capabilities advertised: loadSession true, promptCapabilities {text}.

## Gap inventory (verified against src/isaac/comm/acp, 2026-07-07)

1. **Replay on attached session/new** — child bean (first, blocks real use today).
2. **session/request_permission** — no tool-call approval flow; the agent acts without asking. Biggest functional/safety gap.
3. **Prompt capabilities beyond text** — no image/audio/embeddedContext; client attachments dropped.
4. **Client fs methods** (fs/read_text_file, fs/write_text_file) — agent cannot touch the CLIENT's filesystem; matters most for remote ACP over the cli pipe.
5. **Terminal capability** — no client-hosted terminal execution.
6. **authenticate** — absent (fine on trusted stdio; relevant for untrusted transports).
7. **mcpServers on session/new** — client-provided MCP servers ignored.
8. **Session modes / model selection** (session/set_mode, current_mode_update) — no mid-session switching from the client.
9. **plan sessionUpdate** — no task-plan streaming to client UI.
10. **Non-standard chat/status notification** — custom notification standard clients silently drop; audit what rides on it and either standardize or accept invisibility.

Prioritize children roughly in that order; each is its own bean, spec'd individually.
