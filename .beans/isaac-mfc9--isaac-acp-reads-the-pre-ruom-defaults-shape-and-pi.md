---
# isaac-mfc9
title: isaac-acp reads the pre-ruom :defaults shape and pins pre-ruom foundation/agent — the isaac-0r95 migration never reached acp
status: todo
type: bug
priority: high
created_at: 2026-09-24T21:34:13Z
updated_at: 2026-09-24T21:34:13Z
---

Found 2026-09-24 while landing isaac-j95x: repinning isaac-acp's deps to the landed agent (8cfd44d) fails to load — `Unable to resolve symbol: schema-compose/resolve-entity-templates` — because acp still pins a pre-ruom isaac-foundation (1afd934…) and agent (8aecfc3a…) and isaac-episodes 0cbe24b5…. isaac-0r95 ("seven repos landed") migrated episodes, hail, cron, hooks, http, gchat and gmail but not acp.

## Why it matters now
zanebot runs the ruom agent (da9214a → 8cfd44d) with acp 0.1.15. Every acp crew fallback is `(get-in cfg [:defaults :crew])` (`cli.clj` :137/:205, `server.clj` :180/:249/:263). Under the new shape that key is the crew *template map*, not the crew id, so an `isaac acp` with no `--crew` resolves the crew to a map — broken default-crew ACP sessions on any migrated host. (`--crew marvin` works, which is why the j95x smoke passed.)

## Fix (isaac-acp)
- Replace every `[:defaults :crew]` read with the isaac-agent accessor (`isaac.config.defaults/crew-id`, as gchat/gmail/hooks did under 0r95).
- Repin foundation → current main (≥ d90c209), agent → 8cfd44d, episodes → 01b538a in deps.edn AND bb.edn; migrate feature/spec fixtures that write the flat `:defaults` shape to entity templates (0r95's other repos show the pattern).
- Scenario: `isaac acp` with no `--crew` on a root with `:defaults {:frequencies {:crew :marvin} …}` → session/new opens a marvin session.
- `bb ci` green; version bump; registry repin; zanebot upgrade + restart.

Repo scope: isaac-acp. Read-only in isaac-agent (accessor) and the 0r95-migrated repos for the fixture pattern.
