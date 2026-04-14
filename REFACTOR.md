# Refactoring Lessons

## Shared code extraction is not finished until callers use it

A refactoring is not "new code plus new tests living beside the old code."
It is complete only when existing production callers have been moved to the extracted code and duplication has been removed.

If an extracted namespace, class, or helper has no production callers yet, stop and treat that as a warning sign: the work is incomplete and likely drifting into side-car development.

## Preserve behavior before improving structure

Start from green tests.

Make the smallest possible extraction seam, immediately rewire an existing caller to use it, and run the relevant tests after each tiny step.
Only then continue removing duplication.

The sequence is:

1. Start green.
2. Extract one small helper.
3. Rewire real production code to use it.
4. Run tests.
5. Remove the now-redundant duplication.
6. Run tests again.

That is refactoring. Anything else risks adding unused, tested code without changing the design that actually runs.

## Do not change semantics during extraction

Behavioral drift during extraction is easy to miss, especially when the new code is not yet wired into production.

Concrete example:

- `normalize-envelope` was changed incorrectly.
- An empty envelope began producing `{:jsonrpc "2.0", :id 3, :result nil}`.
- Correct behavior was `nil`.
- Root cause: success response logic was gated only by `notify?` being false.
- Correct guard: require `(contains? result :result)` before constructing a success response.

This is exactly the sort of bug that slips in when building parallel code instead of moving live callers incrementally.

## Extract Method/Function

When a piece of logic or setup repeats, extract the smallest useful function, name it from the caller's point of view, and immediately switch the callers over.

Concrete steps followed here:

1. Spot repeated test setup.
   - `rpc_spec.clj` built the same `BufferedReader`/`StringReader` combination more than once.
2. Extract a tiny helper.
   - Added `(defn reader-for [line] (BufferedReader. (StringReader. line)))`.
3. Rewire all existing callers.
   - Replace repeated reader construction with `reader-for`.
4. Run tests.
   - Verify behavior is unchanged.
5. Keep going only if more duplication remains.
   - The same pattern was applied to JSON-RPC data construction.
6. Extract small protocol helpers.
   - Added `jrpc/request-line`, `jrpc/request`, and `jrpc/result`.
7. Rewire specs to use the extracted helpers.
   - Literal request/result construction moved to `isaac.acp.jsonrpc` helpers.
8. Run tests again.
   - Confirm the extraction changed structure, not behavior.

Why this worked:

- The extracted functions were small and specific.
- Callers were updated immediately, so duplication actually disappeared.
- Each step was testable in isolation.

This is the important part people skip: extraction is not finished when the helper exists. It is finished when the old call sites are gone.

## Extract Class/Namespace

"Extract Class" is the traditional refactoring name.
In Clojure, the equivalent move is usually extracting a cohesive namespace.
The goal is the same: move a tightly related set of behavior and data-shaping responsibilities out of an overgrown module and into a focused unit with a clear purpose.

Concrete example:

- `isaac.acp.jsonrpc` became the natural home for JSON-RPC request/response construction and protocol constants.
- The extraction should group related behavior, not create a vague helper bucket.
- If the new namespace starts sounding like `util`, `common`, or `shared`, the design is probably hanging crooked.

How to do it safely:

1. Start with behavior already covered by tests.
2. Identify one cohesive responsibility inside the existing namespace.
3. Extract only the functions and constants that belong together.
4. Rewire production callers immediately to use the new namespace.
5. Run the relevant specs and features.
6. Remove the old duplicated logic.
7. Run tests again.

What counts as done:

- production callers use the extracted namespace,
- duplicated logic in the old location is removed,
- behavior is unchanged,
- the new namespace has a crisp responsibility.

What does not count:

- adding a new namespace beside the old code without rewiring callers,
- leaving duplicate protocol-building logic in both places,
- dumping unrelated helpers into the extracted namespace just because they are "shared."

This is why `isaac.acp.jsonrpc` is a good example. It is not a grab bag. It is a protocol-focused namespace with a narrow reason to change.

## Replace Magic Number with Constant

If a numeric literal carries protocol or domain meaning, give it a name and use that name everywhere.

Concrete example:

- JSON-RPC parse error code `-32700` appeared in production code and specs.
- That value is protocol knowledge, not local implementation detail.
- Extract `PARSE_ERROR` into `isaac.acp.jsonrpc`.
- Replace every use of `-32700` with `jrpc/PARSE_ERROR`.

This keeps the meaning centralized, removes repeated knowledge, and makes future protocol changes less error-prone.

## Practical rule

When extracting shared logic:

- do not leave the old implementation in place while adding a mirrored new one,
- do not write isolated tests for code that production does not use yet unless they support an immediate rewiring step,
- do not commit until the extraction is wired through real callers, duplication is removed, and the test suite is green.

A fun little compiler-adjacent truth: dead code with tests still has negative design value. It proves something correct that the system does not actually do.

