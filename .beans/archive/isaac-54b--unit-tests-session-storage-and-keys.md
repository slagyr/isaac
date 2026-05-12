---
# isaac-54b
title: "Unit tests: session storage and keys"
status: completed
type: task
priority: high
created_at: 2026-04-02T00:16:11Z
updated_at: 2026-04-02T00:29:06Z
---

## Description

spec/isaac/session/storage_spec.clj and spec/isaac/session/key_spec.clj are missing.

These namespaces are foundational — everything depends on them. Cover:
- storage: create-session!, append-message!, get-transcript, list-sessions, update-tokens!, append-compaction!, one-session-per-key
- key: build-key, parse-key, build-thread-key

Use TDD skill. Follow speclj conventions.

