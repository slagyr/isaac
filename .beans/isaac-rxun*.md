
## Note for whoever resumes (2026-09-21, scrapper@2026-06-29-1749-iaqu)

I picked up the verify-fail, then this session was re-dispatched to another
project mid-turn. Only the **MINOR** item is done; the BLOCKING one is not
started. Recording what exists so it is not redone:

- **isaac-foundation `bean/isaac-rxun` recreated off `main` 22694fc, pushed @
  `d66bc05`** — `parse/substitute-env-recursive` now keeps an explicit `nil`
  inside a sequence. `keep-indexed` discarded every `nil` return, so the
  "explicit nil is kept" promise held only in the map branch; the seq branch
  now marks a dropped entry with a private sentinel and removes just that.
  Spec added beside the map one: "keeps an explicit nil inside a sequence,
  where position is meaning". `bb ci` green — 1131 spec / 0, 198 features / 0
  / 2 pre-existing pendings (the `cli/modules_pins.feature` reds are the stale
  `~/.gitlibs/_repos/file/REL/fixture-agent` mirror; `rm -rf` it first).
- **isaac-agent: untouched.** `bean/isaac-rxun` is still perceptor's `cc44840`
  (my `5d4a655` + the repin of all 18 foundation coordinates to `22694fc`).
  The ambient-snapshot blocker in `openai/shared.clj` `missing-auth-error` is
  open. Reading for whoever takes it: the call sites that have the root config
  in hand are `charge/ensure-provider` (`src/isaac/charge.clj:90`) and
  `config.resolve/resolve-crew-context` (`src/isaac/config/resolve.clj:104`) —
  both already build the enriched provider slice from the root `cfg`, so
  option (a) is a matter of stamping the one `unresolved-ref` for
  `providers.<name>.api-key` onto that slice there and letting
  `missing-auth-error` read it off the value it is already given. That is
  option (b) in perceptor's framing and needs no foundation change.
