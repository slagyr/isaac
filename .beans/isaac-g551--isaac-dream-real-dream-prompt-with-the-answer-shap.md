---
# isaac-g551
title: 'isaac-dream: real dream prompt with the answer shape, and parse fenced JSON replies'
status: todo
type: bug
priority: high
tags:
    - dream
created_at: 2026-11-16T00:31:44Z
updated_at: 2026-11-16T00:31:44Z
parent: isaac-pcm3
---

Repo: **isaac-dream** (`src/isaac/dream/run.clj`, `dream-batch!`). Found on the first real dream on zanebot, 2026-11-15 (Zane, one week, model micah-opus / claude-opus-5).

## Symptom

The dream failed on its first batch: diary `:status "failed"`, error
`Unexpected character ('`' (code 96))` at line 1, column 1. Opus wrapped its JSON
answer in a markdown code fence and `json/parse-string` choked on it.

## Cause

1. **The dream prompt is a stub.** It is one inline line: "Find lasting habits in
   these conversations. Respond with JSON propose and reinforce." It never states
   the answer shape isaac-p36b specifies, so a real model guesses field names, and
   evidence validation would drop what it cites. The grover scenarios scripted the
   exact JSON, so nothing noticed.
2. **The answer parse is brittle.** Any prose or fence around the JSON fails the
   whole dream.

## Fix

- Ship the dream prompt as a module resource (isaac-p36b: "the dream prompt that
  ships with the module"), not an inline string. It explains the task (lasting
  lessons about how this crew should work with its people, not one-off facts),
  gives the exact JSON shape with an example, and says how to cite evidence:
  - `propose`: `[{id (short slug), lesson, evidence [<session>#<n> …], supersedes [habit ids]}]`
  - `reinforce`: `[{id (active habit id), evidence [<session>#<n> …]}]`
  - empty lists are a valid answer; answer with the JSON only.
- Parse tolerantly: take the JSON object out of a fenced or prose-wrapped reply.
  A reply with no JSON object still fails the dream with a clear diary error that
  includes the start of the reply.

## Acceptance

- Scenario: the model replies with the JSON inside a ```json fence plus a lead-in
  sentence; the dream succeeds and stores the proposed habit with its evidence.
- Scenario: the model replies with prose and no JSON; the diary says `failed`
  with an error naming the problem.
- Scenario (or spec): the prompt sent to the model contains the answer shape
  and the labelled window lines.
- `bb spec` / `bb features` green; bump the module version.
- Coordinate with isaac-qv5r (same function); rebase onto it if it lands first.
