---
# isaac-48dx
title: "Rename session index fields to kebab-case"
status: completed
type: task
priority: low
created_at: 2026-04-28T00:14:00Z
updated_at: 2026-04-28T03:21:10Z
---

## Description

Session record fields use camelCase: compactionCount, inputTokens, outputTokens, totalTokens, sessionFile, updatedAt, lastChannel, lastTo. Migrate to kebab-case for Clojure-native consistency.

Scope:
- src/isaac/session/storage.clj — read/write side. Update keyword references in create-session!, update-session!, append-message!, list-sessions, etc.
- features/session/storage.feature, features/session/context_management.feature, etc. — all 'the following sessions match:' / 'has sessions:' tables that mention these columns.
- spec/isaac/features/steps/session.clj — create-session-from-row! reads camelCase column names; update mapping.
- Any reference in src/isaac/server/, src/isaac/drive/, src/isaac/comm/ that destructures these fields.
- features/comm/discord/, features/cron/, features/delivery/ — anywhere session fields surface in feature tables.

Migration of existing session JSONL files:
- The JSONL transcripts live in <state-dir>/sessions/<key>.jsonl with each line containing a session/message/compaction entry. Field names appear in session header lines and in the sessions/index.edn map.
- Read-side: accept both shapes during a deprecation window so existing files don't break. Loader normalizes to kebab on read.
- Write-side: emit kebab-case only.
- Optional: one-shot rewrite tool that batch-converts existing files.

Note: this is a wire-format change. A migration of zanebot's existing sessions will be needed before the deprecation window closes, or we keep the read-fallback indefinitely.

Acceptance:
1. grep -rn 'compactionCount\|inputTokens\|outputTokens\|totalTokens\|sessionFile\|updatedAt\|lastChannel\|lastTo' src/ spec/ features/ returns no matches in production code or fresh writes (legacy JSONL on disk may still contain camelCase keys until migrated).
2. New sessions write kebab-case.
3. Reading existing camelCase JSONL still works.
4. bb features and bb spec pass.

## Notes

Verification failed: acceptance criterion 1 is still not met. grep for camelCase session index fields still finds production code references in src/isaac/session/storage.clj:64-72 (legacy-session-entry-keys maps compactionCount/inputTokens/lastChannel/lastTo/outputTokens/sessionFile/totalTokens/updatedAt to kebab-case). Full bb spec and bb features are green, and the migration behavior appears otherwise correct, but the bead's explicit grep requirement is not yet satisfied.

