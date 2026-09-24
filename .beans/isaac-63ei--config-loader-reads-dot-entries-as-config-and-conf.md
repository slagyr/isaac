---
# isaac-63ei
title: Config loader reads dot-entries as config, and config get disagrees with config validate
status: in-progress
type: bug
priority: high
created_at: 2026-09-24T13:32:28Z
updated_at: 2026-09-24T13:55:50Z
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

## Landed on main (2026-09-24)

main-sha: isaac-foundation 24800a85e0c4fe7ff6c1a8e2f4b2c5b60e24b2c1

`bb ci` green: **1236 specs / 0 failures**, **219 features / 2 failures** — both
the pre-existing `features/cli/modules_pins.feature` stale-`~/.gitlibs` ones,
identical on `origin/main` at 9ab2527. `bb jvm-spec` has the same 8 pre-existing
failures (module lifecycle / protocol, isaac-jf80 + isaac-3rxx) before and
after; none added. GitHub CI green on the landed commit.

### Reproduced first, on a fixture tree

`config/isaac.edn` + `config/crew/marvin.edn` + `config/crew/.removed-20260915/claude.edn`:

    $ bb isaac --root <fx> config validate
    warning: :crew..removed-20260915.claude - unknown key
    OK - config is valid
    $ bb isaac --root <fx> config get defaults
    java.io.FileNotFoundException: <fx>/config/crew/.removed-20260915 (Is a directory)

Both symptoms from the bean, verbatim.

**Defect 2 is not hidden-dir-specific.** A second fixture with an ordinary,
visible entity directory — `config/crew/keaton/_.edn`, isaac-49zp's flagship
form — reproduces the same crash:

    $ bb isaac --root <fx2> config validate   → OK - config is valid
    $ bb isaac --root <fx2> config get        → FileNotFoundException: …/config/crew/keaton (Is a directory)

So `config get` was broken for *every* entity-directory tree, not only for the
stray backup on yopp. Both fixtures now behave: no spurious key, no crash.

### Root causes

1. **Dot-entries.** `tree/classify-child` was not the only scanner:
   `entities/read-dir-files` and `entities/read-entity-dirs` walk
   `config/<key>/` themselves and never go through it — they are what promoted
   `.removed-20260915/` to an entity. All three now skip a name starting with
   `.`, via a shared `paths/hidden-name?`. `paths/config-file?` no longer
   matches a path with a hidden segment (so hot reload does not fire on one),
   and `config reformat` skips them.

2. **get vs validate.** The CLI resolves config once and threads the load result
   into every subcommand (isaac-v1la). `config get` is the only command that
   then *re-reads* its `:sources` off disk — to find the `${VAR}` tokens it must
   redact (`cli/common/threaded-env-redactions`). Since isaac-49zp a source may
   be a **directory** (`config/crew/<id>/`), and `fs/exists?` answers true for a
   directory while `fs/slurp` throws. `validate` never re-reads, so it certified
   a tree `get` could not read. A directory source now stands for the files
   inside it: the tokens are still found and the secrets in them still redacted.
   Simply skipping directories would have been a silent secret leak.

   This is one site missing a guard, not a deep structural divergence — both
   commands go through the same `loader/load-config-result`, and a differential
   sweep over six trees (malformed EDN, inline-vs-slice conflict, file-vs-dir
   conflict, nested entity dir, all-hidden, no config) shows them agreeing on
   every one. Unchanged and out of scope: `get` prints the resolved config even
   when validation reports errors (only a *missing* config is fatal to it).
   That is a reporting-policy difference that predates isaac-49zp, not a
   disagreement about what the config tree is.

### Why the suite could not see it

Two blind spots, both now closed:

- Specs call the subcommands as `(run {:root …} …)` with no threaded `:config`,
  so the redaction branch was never taken.
- mem-fs answers `exists?` **false** for a trailing-slash directory path, while
  RealFs answers true and then throws on slurp — so even a threaded spec would
  have passed.

`spec/isaac/config/cli/get_spec.clj` now threads opts exactly as `isaac.main`
does, and `spec/isaac/config/cli/common_spec.clj` makes mem-fs answer like disk
(`like-real-fs`) and reproduces the `FileNotFoundException` before the fix.

### Coverage added (18 examples)

- `paths_spec` — `config-file?` rejects a dot-entry at any depth; `hidden-name?`.
- `tree_spec` — dot-file and dot-directory at the config root, inside a key's
  directory, and inside a map read by `read-map-dir`.
- `load_result_spec` — six loader-level examples, each asserting the **whole**
  load result is byte-identical to the same tree without the dot-entry: hidden
  file and hidden directory at the config root, hidden file and hidden directory
  inside an entity dir, hidden entries inside an entity's own directory, and all
  of them at once.
- `get_spec` — `get` and `validate` both accept a tree whose entity is a
  directory; `get` reads a subtree path off it; a secret in a file inside an
  entity directory is still redacted.
- `common_spec` — a directory source is read as its files, and does not throw.

No `.feature` file was edited.

### Repin

isaac-agent pins foundation at `9ab2527`, the parent of this commit, so nothing
downstream breaks and no repin is *required*: the change adds one public fn
(`paths/hidden-name?`) and otherwise touches private fns only, and no sibling
repo references any changed surface. A repin train is needed only to ship the
fix to hosts — worth doing, since until then `isaac config get` remains broken
on any host using an entity directory.
