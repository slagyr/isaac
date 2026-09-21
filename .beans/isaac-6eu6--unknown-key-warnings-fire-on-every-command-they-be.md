---
# isaac-6eu6
title: Unknown-key warnings fire on every command; they belong on validation and config change
status: todo
type: bug
priority: normal
created_at: 2026-09-21T20:59:05Z
updated_at: 2026-09-21T20:59:05Z
---

Repo: **isaac-foundation** (`src/isaac/config/warnings.clj:147`).
Follow-up to isaac-nq4c, found in use on zanebot the day nq4c reached the host.

## What happens

Every CLI invocation prints the unknown-key warnings, because every invocation
loads config. `isaac service stop`, `isaac sessions unset`, `isaac prompt` — all
of them emit the full list before doing their actual job:

    {:level :warn, :event :config/unknown-key, :path "hail-settings.beans-repos", …}
    {:level :warn, :event :config/unknown-key, :path "models.glm-5-3.extra-system-prompt", …}

**`--log-level error` does not suppress them.** They are emitted during config
load, before the threshold is applied, so the one obvious mute does not work.

## Why it matters

nq4c is right that an unknown key should not vanish silently — the two keys
still warning on zanebot are both real signal:

- `models.glm-5-3.extra-system-prompt` is deliberate forward-config, commented
  `;; isaac-5n68: not yet a known key; the loader warns and drops it until 5n68
  lands`. The warning is exactly the reminder 5n68 has not shipped.
- `hail-settings.beans-repos` appears only in bean bodies (isaac-fgo0), never in
  source — likely the same pattern.

But a warning that prints on *every command* is a warning nobody reads. Micah's
words: "it should only be on validation steps that it prints those warnings.
Anytime I set a key or anytime we change config, it would make sense to print
those, but just stopping the service, no." Constant output trains the reader to
ignore it, which costs nq4c the very thing it was built for.

## Change

Emit `:config/unknown-key` on the paths where someone is asking a question about
their config, not on every load:

- `isaac config validate` — always.
- A config **change**: `config set`, an edit picked up by hot-reload, a reload.
- Not on an ordinary command that merely happens to load config.

Also make the warning respect the log threshold, so `--log-level error` mutes it
like everything else.

## Acceptance

- `isaac service stop`, `isaac sessions unset <id>.<path>` and `isaac prompt`
  emit no `:config/unknown-key` lines with a config carrying an unknown key.
- `isaac config validate` still lists every unknown key, unchanged.
- A config change that introduces or reloads an unknown key warns once.
- `isaac --log-level error config validate` suppresses them, proving the
  threshold applies.
- Spec coverage for the load-vs-validate split.

## Cleanup already done (2026-09-21, planner)

Three genuinely stale keys removed from zanebot's config — the `:prefer` keys in
`hail/_isaac-template.edn`, `_orchestration-template.edn` and
`_tono-template.edn`. isaac446t had already recorded `:prefer` as retired in
July ("a separate _orchestration-template still carries a stray :prefer — noted
for cleanup"); nq4c is what finally made it visible. Backups alongside as
`*.bak-20260921-prefer`.

**Two of those files are version-controlled** and will return on the next
install unless fixed at source:

- `_orchestration-template.edn` → `slagyr/orchestration` (`isaac-beans/config/hail/`)
- `_tono-template.edn` → `tonotop/planning` (`orchestration/config/hail/`)

The remaining two warnings were left in place deliberately; they are forward
config, not litter.
