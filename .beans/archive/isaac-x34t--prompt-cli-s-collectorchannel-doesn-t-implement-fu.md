---
# isaac-x34t
title: "prompt CLI's CollectorChannel doesn't implement full Comm protocol"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T23:21:56Z
updated_at: 2026-04-29T00:56:49Z
---

## Description

src/isaac/cli/prompt.clj:15-22 defines CollectorChannel but only
implements a subset of the Comm protocol (src/isaac/comm.clj):

  Implemented:    on-turn-start, on-text-chunk, on-tool-call,
                  on-tool-result, on-turn-end, on-error
  Missing:        on-thought-chunk (added by isaac-aept), on-tool-cancel

Symptom: isaac prompt will throw "no method on protocol" the first
time the runtime emits a thought chunk (e.g. during compaction's
"compacting..." status from src/isaac/drive/turn.clj:331) or
cancels a tool.

## Fix

1. Extend CollectorChannel with no-op implementations of
   on-thought-chunk and on-tool-cancel (mirror the null-channel
   shape; the prompt CLI doesn't surface either to the user).

2. Add a spec that asserts every Comm implementation in src/
   responds to every method of the protocol. Catches the next
   protocol-extension oversight at build time. Alternatively, a
   reflection-based discovery test that walks isaac.comm.* namespaces
   and verifies satisfies?/method coverage for each deftype.

## Definition of done

- isaac prompt runs through a compaction event without throwing
- bb spec covers the protocol-completeness check across all comm
  deftypes (cli, acp, discord, memory, null, prompt's collector,
  any future ones)
- bb spec and bb features green

