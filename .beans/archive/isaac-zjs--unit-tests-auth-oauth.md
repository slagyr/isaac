---
# isaac-zjs
title: "Unit tests: auth/oauth"
status: completed
type: task
priority: normal
created_at: 2026-04-02T00:16:13Z
updated_at: 2026-04-02T00:46:31Z
---

## Description

spec/isaac/auth/oauth_spec.clj is missing.

Cover:
- read-credentials from file
- read-credentials from macOS Keychain (once isaac-jjf is done)
- token-expired? logic
- resolve-token fallback chain (file → keychain → error)

Use TDD skill. Follow speclj conventions.

