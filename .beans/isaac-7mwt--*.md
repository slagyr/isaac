
## Work notes (scrapper@isaac-work-1, 2026-09-24)

Implemented on isaac-agent branch `bean/isaac-7mwt` (commit 1f623c9):
- charge.clj: `:tools` schema key + `charge/build` carries it (request → dispatch! → build unchanged path).
- drive/turn.clj: `withhold-charge-tools` subtracts the charge's `:deny` list (names/allowed? matching — :ns/name, ns__name, :ns/*) from cascade + skill auto-tools in build-turn; logs `:turn/tools-withheld` (debug, :tools sorted wire names) only when something is removed.
- Specs: charge_spec (+2), turn_spec (+5: withhold + defs exclude lens__read + log; no :tools unchanged; already-excluded deny no-op/no log; wire spelling; glob).
- Version 0.1.82 → 0.1.83, CHANGELOG entry.
- bb spec: 1787/0. bb lint: 518 errors/146 warnings, same as baseline (none in touched code).
- bb jvm-spec: 1787/4. All 4 in spec/isaac/agent/manifest_spec.clj and pre-existing: test-resources/isaac-manifest.edn shadows resources/ on the JVM classpath. Not touched here.
