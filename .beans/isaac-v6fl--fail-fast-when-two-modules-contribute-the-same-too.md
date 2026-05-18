---
# isaac-v6fl
title: fail-fast when two modules contribute the same :tools or :slash-commands name
status: scrapped
type: bug
priority: normal
created_at: 2026-05-18T22:19:39Z
updated_at: 2026-05-18T22:45:29Z
---

## Problem

`:tools` and `:slash-commands` are keyed by name. Today, `find-tool-manifest-entry` (src/isaac/config/loader.clj:564) and `find-slash-command-manifest-entry` (loader.clj:570) use `some` over the module-index — silent last-loaded-wins when two modules declare the same name. There's no error, no warning; the user has no way to tell something was shadowed.

## Fix

At module-index build time, walk every loaded manifest's `:tools` and `:slash-commands` keys, detect duplicates, and surface them as load errors with both contributing module ids. The same check applies to any future flat-namespaced manifest slot.

`:comm` and `:provider` are NOT affected by this — they're keyed by `:type` inside the user's slot config, so duplicates across manifests are user-discriminated. This bean is just for the flat-name surfaces.

## Open questions before scenarios

- Hard error vs. warning? Hard error is safer (silently-overridden tools are a security/correctness issue) but could break workflows where modules legitimately re-export the same name. Recommend hard error.
- What about a module providing the same name as the core manifest? (`:tools :read` — core declares it.) Same hard error, or special "core takes precedence" path? Recommend hard error — modules shouldn't shadow core builtins.

## Status

Draft. Needs scenarios + design confirmation before promoting.


## Reasons for Scrapping

The premise is wrong. Users should be able to override the behavior of a tool or slash-command by declaring a module that contributes the same name — that's a feature, not a collision. Today's last-loaded-wins behavior matches the intent.

If two unrelated third-party modules happen to share a name, the user's `:modules` declaration order picks the winner; that's enough.
