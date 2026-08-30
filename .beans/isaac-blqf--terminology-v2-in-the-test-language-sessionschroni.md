---
# isaac-blqf
title: 'Terminology v2 in the test language: sessions→chronicles/threads in step names'
status: draft
type: task
priority: low
created_at: 2026-08-30T22:48:18Z
updated_at: 2026-08-30T22:48:18Z
---

Repo: isaac-agent (spec-support steps) + downstream feature files. Per isaac-51xy Terminology v2 the internal vocabulary is chronicle/thread/episode; the industry word 'session' stays at EXTERNAL boundaries only (CLI flags, ACP ids). The Gherkin step language is internal: 'the following sessions exist' → 'the following chronicles exist', 'session {k} has transcript' → 'chronicle {k} has transcript', etc. Keep old phrases as aliases during migration; sweep features repo-by-repo; remove aliases in a final pass. Micah 2026-08-30: worth its own bean; do NOT ride scuttlebutt. Also audit step docstrings that say 'session' for internal senses. Draft until the rename inventory (gherclj steps | grep -i session across repos) is attached and scenarios/mechanics decided.
