# SMELLS

## Dead code

Dead code is code with no meaningful runtime use. It should generally be removed.

### Untested dead code

Untested dead code can usually be removed immediately. Snip.

If nothing exercises it, there is little evidence that anyone depends on it. Keeping it around adds noise, increases maintenance cost, and makes the real execution path harder to see.

### Tested dead code

Tested dead code is trickier.

Sometimes it remains because it is part of a public API. Sometimes it is scaffolding for a partially built feature. In either case, the code should be accompanied by a comment explaining why it still exists and who or what depends on it.

That comment is not a pardon. Even tested dead code should eventually be removed.

## Unused bindings

Unused binding symbols should be prefixed with an underscore to show they are intentionally unused.  

Use `_foo`, not `foo`, when the value is bound but ignored.

A single underscore is enough when the name doesn't matter.  `(catch Exception _)`

## Duplication

Duplication is repeated knowledge. A copied line is not always a problem; repeated intent usually is.

Small duplication can be tolerated while code is in motion. Once the shape settles, extract the shared idea so changes happen in one place.

Be careful not to extract too early. Two similar lines do not automatically justify a helper. The helper should make the code clearer, not merely shorter.

For example, repeated `(BufferedReader. (StringReader. ...))` setup in a spec can justify a tiny helper like `reader-for` once the intent is clear. That keeps the setup in one place without inventing a grand abstraction.

## Magic numbers

A magic number is a literal whose meaning depends on hidden domain knowledge.

If a number is part of a protocol, file format, or business rule, name it once and use the named constant everywhere.

This is especially important when the same literal appears in both production code and tests. That is repeated knowledge with poor affordance.

For example, JSON-RPC error code `-32700` should be represented by a named constant such as `PARSE_ERROR`, not repeated inline across namespaces.

## Testing smells

Tests should fail in ways that explain what went wrong.

A bare `(should false)` is a smell because it throws away the reason for the failure. It signals "something bad happened" without preserving the expected behavior or the missing exception.

Prefer an explicit failing assertion with a message, for example `(should-fail "Expected malformed JSON to throw ExceptionInfo")`, or better yet assert directly on the exception or result you expect.
