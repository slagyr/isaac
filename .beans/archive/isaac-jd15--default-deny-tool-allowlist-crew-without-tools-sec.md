---
# isaac-jd15
title: "Default-deny tool allowlist: crew without :tools section gets no tools"
status: completed
type: bug
priority: high
created_at: 2026-04-20T17:50:53Z
updated_at: 2026-04-20T19:06:47Z
---

## Description

Today, a crew config with no :tools key receives every registered tool (including exec). The security contract should be default-deny: no :tools section means zero tools. See features/tools/allowlist.feature for the two @wip scenarios that lock in the behavior.

## Acceptance Criteria

1. Remove @wip from the two new scenarios in features/tools/allowlist.feature
2. bb features features/tools/allowlist.feature:94 passes
3. bb features features/tools/allowlist.feature:105 passes
4. bb features passes
5. bb spec passes

## Design

Two implementation options:
1. In src/isaac/drive/turn.clj allowed-tool-names: always return a set (possibly empty) instead of nil when :tools is absent.
2. Flip nil semantics in src/isaac/tool/registry.clj and src/isaac/tool/builtin.clj allowed-tool?: nil means deny all, not allow all.

Option 2 is the deeper fix: it makes the default-deny invariant true at every layer, not just at the drive/turn boundary. Recommend Option 2.

Crews that today implicitly rely on 'no :tools = all tools' will break — that's intentional. Marvin's real config (~/.isaac/config/crew/marvin.edn) is one such crew; expect user to explicitly allowlist tools after this lands.

