---
# isaac-rxun
title: 'Unresolvable ${VAR} and ${file:…} references: warn, treat as unset, never send the literal'
status: in-progress
type: bug
priority: normal
created_at: 2026-09-21T16:28:51Z
updated_at: 2026-09-21T16:45:41Z
---

Repo: **isaac-foundation** (`src/isaac/config/parse.clj`).

## The footgun

`substitute-env` (`parse.clj:42`) replaces `${VAR}` with the variable's value,
or **leaves the literal text** when the variable is unset:

```clojure
(or (env/env var-name) match)
```

So `:api-key "${OPENAI_ZANE_EMBEDDING_API_KEY}"` with the variable missing from
the server's environment becomes the literal string
`${OPENAI_ZANE_EMBEDDING_API_KEY}`. Validation passes, the literal goes out as
the key, and the provider answers 401. The error blames auth, not the missing
variable. The usual cause: the variable is set in the operator's shell, so
manual tests work, but not in the launchd/systemd environment the service runs
under.

`${file:…}` references (isaac-jl9p) have the same problem with a missing file.

## Decision (Micah, 2026-09-21)

Report it; **don't block writing config** because a variable or file is
missing.

## Design

1. **Never pass the literal through.** A reference that can't be resolved
   resolves to *absent*, as if the field weren't set. The literal looks like
   a real value and gets sent. It's the worst of both outcomes.
2. **Always warn**, naming the field and the reason:
   `OPENAI_ZANE_EMBEDDING_API_KEY is not set (used by :episodes :embedding :api-key)`,
   `prompts/glm-batching.md not found (used by :models :glm-5-3 :extra-system-prompt)`.
3. **Whether it becomes an error is decided by the field, not the
   reference.** An unresolvable reference is exactly an unset field, so:
   - a **required** field gets the existing required-field error, with the
     reason attached ("unset because `${X}` is not set")
   - an **optional** field is simply absent, and the feature that needs it
     reports at the point of use. Providers already do this:
     `missing-auth-error` (`isaac-agent/src/isaac/llm/api/openai/shared.clj:106`)
     would say "no API key" instead of the provider's 401. Make that message
     carry the variable name.

   No new error class; the warning always fires.
4. **Where it's checked:**
   - **Writing config** (`config set`, edits): a warning only. Never refuse. The
     writer's environment isn't the server's, so a missing variable in the
     CLI's shell proves nothing. Say so in the warning ("not set in this
     shell; the server's environment may differ"). A file reference may be
     written before the file is created.
   - **`config validate`**: the same warning, with the same caveat for env
     vars.
   - **Server boot and hot reload**: the server's environment is the one that
     counts. Log `:warn` with field and reason. A missing optional value must
     not fail the reload or the boot. That would take down unrelated config
     over one missing key.
5. **Raw reads are unaffected.** `config set` reads with substitution off
   (`config/cli/common.clj:269`), so references survive rewrites as they do
   today.

## Relation to isaac-jl9p

jl9p's rule 2 ("a missing file is a validation error") is superseded by this
bean: files follow the same warn-and-treat-as-unset rule as env vars.

## Done when

- an unset `${VAR}` resolves to absent, never the literal (spec)
- a missing `${file:…}` resolves to absent (spec, once jl9p lands)
- each case warns with field path and reason, in the CLI and in server logs
  (specs)
- `config set` succeeds with an unresolvable reference and warns (spec)
- a required field whose reference can't be resolved gets the required-field
  error with the reason attached (spec)
- server boot and hot reload succeed with an optional unresolvable reference
  (spec)
- a provider with an unresolvable `:api-key` reports the missing variable by
  name rather than letting the provider return 401 (spec)
- `bb verify` and `bb jvm-spec` are both green
