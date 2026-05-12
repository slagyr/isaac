---
# isaac-c8z
title: "Store returned response model in provider messaging flows"
status: completed
type: feature
priority: high
created_at: 2026-04-08T14:59:58Z
updated_at: 2026-04-08T15:15:54Z
---

## Description

Isaac should persist the actual model returned by each provider, not only the configured model. Add provider-specific feature coverage in the existing messaging features under features/providers so response-model behavior is specified alongside each provider's messaging contract. Prefer existing transcript/session matchers over introducing a dedicated assertion step.

## Scope
- Add response-model scenarios to provider messaging feature files
- Verify transcript/session metadata stores the provider-returned model
- Cover fallback behavior when a provider does not return a model
- Keep provider-specific response behavior in features/providers/* rather than chat routing features

## Notes
- Provider wire formats differ, but Isaac normalizes returned model data before persistence
- If needed, add a generic provider stub step such as  for normalized provider result maps
- Consider splitting or thinning features/chat/providers.feature so it focuses on routing/selection only

