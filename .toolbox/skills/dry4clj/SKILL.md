# dry4clj

Use `dry4clj` to find candidate duplicate Clojure code by structure, not exact text.

## When To Use It

Use `dry4clj` when you want to:
- scan a Clojure codebase for duplicated top-level forms
- surface likely duplication before refactoring
- generate machine-readable duplicate reports for follow-up tooling

`dry4clj` is a candidate finder. It does not rewrite code or prove semantic equivalence.

## What It Scans

By default it scans `src` when you do not pass any paths.

It recursively scans these source file types:
- `.clj`
- `.cljc`
- `.cljs`
- `.bb`

It supports reader conditionals for both `:clj` and `:bb`.

## Installation

Requirements:
- `clj` for the JVM entrypoint
- `bb` if you want the babashka task

### Minimal Install In Another Project

1. Copy `src/dry4clj/core.clj` into the target repo at `src/dry4clj/core.clj`.
2. Merge this alias into the target repo's `deps.edn`:

```clojure
{:aliases
 {:dry {:main-opts ["-m" "dry4clj.core"]}}}
```

3. If the target repo uses babashka tasks, merge this into its `bb.edn`:

```clojure
{:paths ["src"]
 :tasks
 {dry {:requires ([dry4clj.core :as dry4clj])
       :task (apply dry4clj/-main *command-line-args*)}}}
```

4. If you also want the `dry4clj` specs in that repo, copy `spec/dry4clj/core_spec.clj` and merge the existing `:spec` alias from this repo's `deps.edn`.

Do not overwrite the target repo's existing aliases or babashka tasks. Merge into them.

## Usage

Run with Clojure:

```bash
clj -M:dry [options] [file-or-directory ...]
```

Run with babashka:

```bash
bb dry [options] [file-or-directory ...]
```

Examples:

```bash
clj -M:dry
clj -M:dry src test
clj -M:dry --edn --threshold 0.9 src
bb dry
bb dry --min-lines 6 --min-nodes 30 src
```

## Options

```text
--threshold N   Minimum structural similarity score, default 0.82
--min-lines N   Minimum source lines in a candidate form, default 4
--min-nodes N   Minimum normalized syntax nodes, default 20
--format F      text or edn, default text
--edn           Same as --format edn
--text          Same as --format text
```

## Output

Default text output:

```text
DUPLICATE score=0.89
  src/foo.clj:12-25
  src/bar.clj:30-44
```

EDN output:

```clojure
{:candidates
 [{:score 0.8909090909090909
   :left {:file "src/foo.clj", :start-line 12, :end-line 25}
   :right {:file "src/bar.clj", :start-line 30, :end-line 44}
   :left-nodes 88
   :right-nodes 91}]}
```

## Tuning Guidance

- Raise `--threshold` to reduce noise.
- Lower `--threshold` to catch looser structural matches.
- Raise `--min-lines` and `--min-nodes` to ignore tiny helpers.
- Start with defaults unless the codebase is unusually small or noisy.

## Limitations

- Only top-level forms are compared.
- Matches are structural, not semantic.
- Similarity is based on normalized syntax fingerprints.
- Reader conditional support is limited to `:clj` and `:bb`.

## Verification

In this repo, verify behavior with:

```bash
clj -M:spec
bb spec
bb dry --help
```
