---
# isaac-tw37
title: "Generalize tool registration with :available? predicate"
status: completed
type: task
priority: low
created_at: 2026-05-08T00:27:23Z
updated_at: 2026-05-08T16:47:31Z
---

## Description

isaac.tool.builtin/register-built-in-tool! special-cases grep:

```clojure
(defn- register-built-in-tool! [registry-ns tool-name]
  (if (= tool-name "grep")
    (if-not (shell/cmd-available? "rg")
      (log/warn :tool/register-skipped :tool "grep" :reason "rg not found on PATH")
      (registry-ns (grep-tool-spec)))
    (when-let [spec (get built-in-tool-specs tool-name)]
      (registry-ns spec))))
```

The grep tool is conditionally registered based on whether `rg` is available on PATH. Today this is a hard-coded branch on the tool name — every other tool that has external dependencies (e.g. a future tool needing `git`, `jq`, `bun`) would require another branch here.

## Proposed change

Add an optional `:available?` predicate to tool specs. Registration consults it, falls back to "always available" if absent:

```clojure
{:name "grep"
 :description "..."
 :parameters {...}
 :available? (fn [] (shell/cmd-available? "rg"))
 :handler #'grep-tool}
```

`register-built-in-tool!` collapses to:

```clojure
(defn- register-built-in-tool! [registry-ns tool-name]
  (when-let [spec (get built-in-tool-specs tool-name)]
    (if-let [pred (:available? spec)]
      (if (pred)
        (registry-ns (dissoc spec :available?))
        (log/warn :tool/register-skipped :tool tool-name :reason "available? returned false"))
      (registry-ns spec))))
```

The grep-tool-spec helper merges into built-in-tool-specs as a regular entry (no longer needs to be a fn-built spec).

## Why this matters for third-party modules

Today there is no way for a third-party module to declare "register only if X is available." After this change, any module-supplied tool can opt into availability gating with the same predicate field. That removes the hard-coded grep branch AND extends the pattern to any tool, built-in or external.

## Out of scope

- Surfacing :available? to the manifest schema (a manifest-driven check could come later if useful, but the predicate-on-spec form is enough for code-driven registration).
- Unregistering tools whose availability changes mid-session.

## Acceptance Criteria

bb spec green; bb features green; isaac.tool.builtin/register-built-in-tool! contains no special-case branch on tool-name "grep"; grep rg-availability check moves into its spec as an :available? predicate; tool-registry/register! strips :available? before storing if needed (or carries it through harmlessly); a unit test covers a tool whose :available? returns false NOT being registered.

