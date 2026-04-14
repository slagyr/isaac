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
