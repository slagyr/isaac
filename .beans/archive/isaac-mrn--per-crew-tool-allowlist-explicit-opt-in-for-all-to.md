---
# isaac-mrn
title: "Per-crew tool allowlist: explicit opt-in for all tools"
status: completed
type: feature
priority: high
created_at: 2026-04-14T20:16:02Z
updated_at: 2026-04-17T01:20:37Z
---

## Description

Each crew member has an explicit list of allowed tools. Only allowed tools are registered for the session and included in the prompt. No allow list means no tools. Exec requires explicit opt-in.

## Design

Tool filtering happens at registration time — before the prompt is built. The model never sees tools it can't use. If the model hallucates a tool call for a disallowed tool, the registry returns an error.

Config in isaac.edn:
```clojure
{:crew {:list [{:id "ketch" :model "grok" :tools {:allow [:read :write :edit :exec]}}
               {:id "marvin" :model "gpt" :tools {:allow [:read :write :edit :exec]}}]}}
```

No :tools key = no tools. Profiles and groups can come later.

## Acceptance criteria
- Crew member with tools.allow gets only those tools in the prompt
- Crew member with no tools config gets no tools
- Disallowed tool calls return an error
- exec requires explicit opt-in

Feature: features/tools/allowlist.feature

## Design

Tool registry filters on crew member allow list. register-all! takes the allow list and only registers matching tools. Prompt builder receives only registered tools. Hallucinated tool calls hit the existing unknown-tool error path in tool-registry/execute.

## Notes

Implemented allowlist filtering: registry/tool-fn/tool-definitions support allowed tool names, builtins can register with an allow list, process-user-input filters prompt tools and tool execution by crew tools.allow, feature steps parse tools.allow and prompt tool assertions. Unit specs and isolated features/tools/allowlist.feature pass. Full bb features is blocked by stale feature contracts that still assume tools are available by default with no tools.allow, e.g. features/acp/proxy.feature, features/session/llm_interaction.feature, features/providers/openai/dispatch.feature, and features/acp/cancel_tool_status.feature.

