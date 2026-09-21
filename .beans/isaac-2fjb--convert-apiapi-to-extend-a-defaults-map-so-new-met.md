---
# isaac-2fjb
title: Convert api/Api to extend + a defaults map so new methods can have defaults
status: todo
type: task
priority: low
created_at: 2026-09-21T16:22:55Z
updated_at: 2026-09-21T16:22:55Z
---

## Why

Adding a method to `isaac.llm.api.protocol/Api` today means editing every
implementer at once. There are seven: messages, ollama, grover, responses,
chat-completions and UnknownApiProvider in isaac-agent, plus ClaudeCliAPI in
isaac-claude-code. They all implement the protocol inline in `deftype`.

A defaults map fixes that: implementers attach the protocol with
`(extend T Api (merge api-defaults {…}))`, and a new method only needs an entry
in `api-defaults`. Micah wants this as the pattern for Isaac's protocols.

## The trap (verified 2026-09-21)

A deftype that implements a protocol **inline** compiles to a Java interface
on the JVM, so an `extend Object` default never reaches it. Calling a method
it lacks throws `AbstractMethodError`. Babashka/SCI protocols are map-based and
fall through to the default without complaint. `bb verify` runs on babashka;
zanebot runs the JVM. A half-converted protocol goes green in CI and throws in
production.

Minimal repro results:

```
                     JVM                  babashka
converted reminder   [:a]  (default)      [:a]  (default)
inline reminder      AbstractMethodError  [:a]
```

## Plan

1. Pure refactor, no behavior change: convert all seven implementers from
   inline `deftype … Api (…)` to a plain `deftype` plus `extend` with the
   merged defaults map.
2. Cross-repo order: **isaac-claude-code converts in the same release as
   isaac-agent introduces `api-defaults`.** Neither half is safe alone once a
   method with a default is added.
3. After this lands, a new `Api` method is a one-place change.

## Done when

- every `Api` implementer uses `extend` + `api-defaults`; no inline
  implementations remain in either repo
- a spec adds a throwaway method with a default and proves every implementer
  answers it, **under `bb jvm-spec`**, not only `bb verify`
- `bb verify` and `bb jvm-spec` are both green in isaac-agent and
  isaac-claude-code
