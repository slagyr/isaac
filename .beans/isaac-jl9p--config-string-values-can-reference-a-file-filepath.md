---
# isaac-jl9p
title: 'Config string values can reference a file: "${file:path}"'
status: todo
type: feature
priority: normal
created_at: 2026-09-21T15:38:42Z
updated_at: 2026-09-21T16:41:58Z
blocked_by:
    - isaac-rxun
---

Repo: **isaac-foundation** (`src/isaac/config/parse.clj`).

## Why

Large text in config (prompts, soon an `:extra-system-prompt` per model,
isaac-5n68) doesn't belong inline in edn. Today the only way to put it in a
file is a **companion**: `<kind>/<id>.md` fills exactly one field named by the
kind's descriptor (crew → `:soul`, berths → `:ledger`, hail/cron → `:prompt`;
`config/entities.clj:136`). That allows one large string per entity, gives no
way to share a file between entities, and doesn't work for anything that
isn't an entity, such as `:defaults`.

## Design

A string value may reference a file with the same `${...}` syntax that
environment variables use:

```clojure
;; models/glm-5-3.edn
{:extra-system-prompt "${file:prompts/glm-batching.md}"
 :compaction-prompt   "${file:prompts/glm-compaction.md}"}
```

Substitution already runs **after** the edn is parsed, walking string values
(`substitute-env-recursive`, `parse.clj:44`). File contents full of quotes,
newlines or backslashes therefore can't break parsing. File references are one
more case in that same walk.

It's backward compatible. Today `${file:x}` is looked up as an env var named
`file:x`, which can't exist, so it's left as literal text. Nothing currently
in use changes meaning.

## Rules

1. **Paths are relative to the config root and must stay inside it.** An
   absolute path, or a relative one that escapes the root (`..`), is an error.
   Otherwise a config line could pull `~/.ssh/id_rsa` into a prompt that gets
   sent to an LLM provider. Staying inside the root also lets the watcher see
   the file.
2. **A missing or unreadable file warns and resolves as unset**, the same as
   an unset env var. The full rule is in isaac-rxun. It
   never passes the literal `${file:…}` through, and it never blocks writing
   config.
3. **Frontmatter is stripped**, using the same `split-frontmatter` as souls and
   rules. A prompt file can carry a description or notes that never reach the
   model.
4. **Editing a referenced file reloads the config that references it**, with
   no restart. Today the watcher only accepts known filename patterns
   (`config/paths.clj:10`, `(berths|crew|cron|hooks)/…md`), so a change to
   `prompts/glm-batching.md` would be ignored. Track the referenced files, or
   accept any file under the config root. Either way, don't add another name
   to a hard-coded list in foundation (the isaac-1pi2 ruling).
5. **Writes keep the reference.** `config set` reads raw config with
   substitution off (`config/cli/common.clj:269`); that's why `${VAR}`
   survives a rewrite. File references must be resolved in the same step, so
   they survive too. A `config set` must never paste a whole prompt into the
   edn.
6. **`config show` displays the reference**, not the contents, unless the
   resolved view is asked for.
7. **Embedding works like env vars.** `"Preamble ${file:x.md}"` substitutes in
   place. The common case is a value that is only the reference.

## Companions: kept (Micah, 2026-09-21)

Keep companions. They need no syntax, and the single-file `.md` entity form
(frontmatter holds the config, body fills the companion field) depends on
them. The overlap is exactly one field per kind: a soul could come from
`crew/bebop.md` or from `:soul "${file:…}"`. Make setting a companion field
both ways on the same entity a validation error, so there's never a question
of which one wins.

The alternative is retiring companions and moving every soul to an explicit
reference. It's cleaner in principle, but it touches every crew on zanebot and
yopp.

## Done when

- `"${file:path}"` resolves to the file's contents, frontmatter stripped
  (spec)
- a path outside the config root is rejected (spec); a missing file warns and
  resolves as unset (spec)
- `config set` on an entity that uses a reference preserves the reference
  (spec)
- `config show` shows the reference; the resolved view shows the contents
  (spec)
- editing a referenced file on a running server reloads the referencing
  entity with no restart (spec, plus a live check on zanebot)
- setting a companion field both inline and via companion is a validation
  error (spec)
- `bb verify` and `bb jvm-spec` are both green

## Unlocks

- isaac-5n68: `:extra-system-prompt` can live in a file
- the defaults restructure (to be filed): large default text without making
  `:defaults` an entity
