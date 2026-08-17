---
# isaac-lq7x
title: Scene files as markdown with YAML frontmatter
status: todo
type: task
created_at: 2026-08-17T16:45:12Z
updated_at: 2026-08-17T16:45:12Z
parent: isaac-51xy
blocked_by:
    - isaac-rxr4
---

Scenes are 90% prose (distilled :text) — store them as what they read as. Replace `<scene-id>.edn` with `<scene-id>.md`: YAML frontmatter (traditional MD convention; matches beans and other isaac md-frontmatter files) carrying the structure — id, start-id, end-id, started-at, ended-at, seal-reason, gist — with the distilled text as the markdown body. Decision (2026-08-17, Micah).

- **Reuse the existing frontmatter component** — isaac already loads md-with-frontmatter (crew .md configs); do not hand-roll a parser.
- `episode.edn` stays EDN (all structure, no prose).
- Update: isaac.episodes.store read/write; episode feature steps that read scene files (`that episode has scenes matching:`, `scene N ... does not contain`); migrate_session.feature assertions if format-sensitive.
- Gist lives in frontmatter (one-liner; what bean 3's index will read alongside the body).
- Do this BEFORE isaac-j2p4 (bean 3) — the index reads scene files and should not chase a moving format. isaac-j2p4 planning should treat scene .md as the input contract.

## Acceptance
- Migrated scenes land as `<scene-id>.md` (YAML frontmatter + distilled-text body); human-readable when opened.
- `bb features features/episodes/migrate_session.feature` green; `bb spec` green.
