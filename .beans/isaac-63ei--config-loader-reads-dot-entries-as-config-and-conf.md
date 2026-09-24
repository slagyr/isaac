---
# isaac-63ei
title: Config loader reads dot-entries as config, and config get disagrees with config validate
status: todo
type: bug
priority: high
created_at: 2026-09-24T13:32:28Z
updated_at: 2026-09-24T13:32:28Z
---

Repo: **isaac-foundation** (`src/isaac/config/tree.clj`, and whatever path
`config get` takes that `config validate` does not).

Found on yopp while migrating it onto isaac-49zp's config layout.

## Two defects, the second worse than the first

### 1. Dot-entries under `config/` are read as config

`classify-child` (`tree.clj:33`) accepts exactly three things — an `.edn` file,
a `.md` file, or **a directory** — and nothing filters hidden names:

    (parse/dir?* (str dir "/" child)) [child :dir]

Under isaac-49zp a directory *is* a key, so any subdirectory becomes a key or
entity regardless of its name. On yopp, `config/crew/.removed-20260915/` — a
backup of a retired `claude` crew, stashed on 2026-09-15 and invisible to the
old loader — was promoted to an entity, its `claude.edn` read as a field:

    warning: :crew..removed-20260915.claude - unknown key

Note the double dot. The loader was doing exactly what 49zp told it to.

Hidden files and directories are conventionally not content — git, build tools
and editors all skip them by default — and stashing a backup as
`.removed-<date>/` next to the thing it replaced is an ordinary habit. 49zp
silently promoted it to configuration.

**Change:** `classify-child` returns nil for any child whose name starts with
`.`, for all three forms. A dot-file is not config.

### 2. `config validate` passes a tree that `config get` cannot read

Same tree, same loader, on yopp before the directory was moved:

    $ isaac config validate
    OK - config is valid

    $ isaac config get defaults
    Type:     java.io.FileNotFoundException
    Message:  /home/yopp/.isaac/config/crew/.removed-20260915 (Is a directory)
    Location: src/isaac/main.clj:202:23

Whatever path `get` takes tries to read the entity as a file; `validate` does
not. This is independently a bug and arguably the more serious of the two:
validation's entire job is to tell an operator the config is sound, and here it
certified a tree that could not be read. An operator who validates before
restarting — the discipline that has saved two live hosts this week — would
have been told everything was fine.

Fixing defect 1 hides this symptom on yopp but does not fix the divergence.
Both paths must agree on what the config tree is.

## Acceptance

- A `.edn`, `.md` or directory under `config/` whose name starts with `.` is
  ignored: no key, no entity, no warning, no error.
- A config tree containing such an entry loads identically to one without it.
- `config get` and `config validate` agree on every tree: any tree one accepts,
  the other accepts, and any tree one rejects, the other rejects with the same
  error.
- Spec coverage for a hidden file and a hidden directory at both the config root
  and inside an entity dir, and a regression test pinning get/validate
  agreement.

## Why now

isaac-69d9 (retire the `:frontmatter?`/`:companion` berth) widens markdown
handling further. This should land first so that work builds on a loader that
agrees with itself.

## Live state

yopp's stray directory was moved to `~/isaac-config-attic/crew-removed-20260915`
rather than deleted, and yopp then loaded clean. No other dot-entry exists under
`config/` on yopp; zanebot has none.
