---
# isaac-5qb5
title: ":jsonl-edn-index SessionStore implementation"
status: completed
type: feature
priority: low
created_at: 2026-05-10T22:22:48Z
updated_at: 2026-05-11T18:17:20Z
---

## Description

The :jsonl-edn-index impl was wired into the :session-store config field but currently falls back silently to :jsonl-edn-sidecar. A real implementation should maintain a single combined index file (sessions/index.edn) instead of per-session sidecar .edn files, making read-session-store a single-file read rather than scanning N sidecar files. This would be faster than :jsonl-edn-sidecar while still being persistent (unlike :memory).

