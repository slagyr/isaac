---
name: gherkin
description: Use this skill when writing, reviewing, or planning Gherkin feature files (.feature). Covers scenario design, step writing, anti-patterns, and acceptance test strategy.
---

# Gherkin

## When This Skill Applies

Use this skill whenever you are writing, reviewing, or planning `.feature` files — regardless of the framework (gherclj, Cucumber, Behave, SpecFlow, etc.).

## Scenario Design

### One behavior per scenario

Each scenario tests one thing. If you need "and also check that..." it's a second scenario.

```gherkin
# GOOD — focused
Scenario: Admin can log in
  Given a user "alice" with role "admin"
  When the user logs in
  Then the response status should be 200

Scenario: Guest is rejected
  Given a user "bob" with role "guest"
  When the user logs in
  Then the response status should be 401
```

```gherkin
# BAD — testing two behaviors
Scenario: Login behavior
  Given a user "alice" with role "admin"
  When the user logs in
  Then the response status should be 200
  Given a user "bob" with role "guest"
  When the user logs in
  Then the response status should be 401
```

### Concrete examples over abstract assertions

Show real data. Don't describe structure.

```gherkin
# GOOD — concrete, verifiable
Then the IR should be:
  """
  {:feature "Login"
   :scenarios [{:scenario "Success"
                :steps [{:type :given :text "a valid user"}]}]}
  """
```

```gherkin
# BAD — vague, hides what matters
Then the IR should have 1 scenario
And the scenario should have 1 step
```

### Scenario names describe the outcome

Name what happens, not what you're testing.

```gherkin
# GOOD
Scenario: Expired token is rejected
Scenario: Empty cart shows helpful message

# BAD
Scenario: Test token validation
Scenario: Cart edge case
```

## Step Writing

### Given — establish context

Set up the world. No assertions, no actions.

```gherkin
Given a user "alice" with role "admin"
Given an empty cart
Given the system has 3 products
```

### When — perform the action

One action per scenario. If you have multiple When steps, you're testing multiple things.

```gherkin
When the user logs in
When the user adds "Widget" to the cart
```

### Then — assert the outcome

Verify observable results. Be specific.

```gherkin
Then the response status should be 200
Then the cart should contain 1 item
Then the error message should be "Invalid credentials"
```

### And/But — continue the previous type

```gherkin
Given a user "alice" with role "admin"
And a valid session token
When the user logs in
Then the response status should be 200
And the response should contain a session cookie
But the response should not contain the password
```

### Data lives in Given; one high-level When

Don't spread input across multiple When steps. Move the data into a Given
table and keep a single business-level action.

```gherkin
# BAD - field-by-field actions
When they enter "https://example.com" as the URL
And they enter "Example Website" as the title
And they submit the bookmark

# GOOD - data in Given, one action
Given a bookmark to add:
  | url   | https://example.com |
  | title | Example Website     |
When they add the bookmark
```

### Horizontal vs vertical tables

Horizontal (header row) for multiple records; vertical (key/value pairs)
for one record with several fields. A one-record horizontal table is a
smell; a many-record vertical table is unreadable.

## When to Use What

### Background vs repeated Given steps

Use Background when every scenario in a feature shares the same setup.

```gherkin
# GOOD — shared setup in Background
Background:
  Given a registered user "alice"

Scenario: View profile
  When alice views her profile
  Then she sees her name

Scenario: Edit profile
  When alice edits her name to "Alice Smith"
  Then her profile shows "Alice Smith"
```

Don't use Background for setup that only some scenarios need.

### Scenario Outline vs individual scenarios

Use Scenario Outline when scenarios differ only in data, not behavior.

```gherkin
# GOOD — same behavior, different data
Scenario Outline: Role-based access
  Given a user with role "<role>"
  When the user accesses the admin panel
  Then the response should be <status>

  Examples:
    | role  | status |
    | admin | 200    |
    | guest | 403    |
    | user  | 403    |
```

Don't use Outline when the scenarios have different steps or different meaning.

### Tables vs doc-strings

Tables for structured, multi-column data:

```gherkin
Given the following users:
  | name  | role  | active |
  | alice | admin | true   |
  | bob   | guest | false  |
```

Doc-strings for blocks of text, code, or data formats:

```gherkin
Then the IR should be:
  """
  {:feature "Login"
   :scenarios [{:scenario "Success"
                :steps [{:type :given :text "a valid user"}]}]}
  """
```

### Tags

Use tags to categorize and filter scenarios:

- `@wip` — not yet implemented
- `@smoke` — critical path, run first
- `@slow` — takes time, skip in fast feedback loops

Tags can go on features (apply to all scenarios) or individual scenarios.

## Style and Discipline

### Entertaining test data

Test data should be fun to read. A sad lemon in the fridge is better
than "test value 1". Hieronymus's emergency lettuce beats "sample
content". Tests are documentation — make people smile when they read
them.

### Tables over narrative, always

Prefer code-like precision. If you find yourself writing
"when the client does X", stop and figure out how to say it as a table
of data. Narrative steps hide the protocol details that matter.

### Request/response symmetry

If the request is tabularized, the response is tabularized. Same
dot-path structure on both sides.

### Strip protocol noise, keep semantic anchors

Constants like `jsonrpc: "2.0"` don't belong in tables — the step
definition adds them. But pivotal fields like `id` that correlate
requests and responses DO belong visible. When a field is really
pivotal, promote it to the step phrase:

```gherkin
When the client sends request 30:
  | key    | value          |
  | method | session/prompt |
```

not

```gherkin
When the client sends:
  | key    | value          |
  | id     | 30             |
  | method | session/prompt |
```

### Domain-scoped step names

`HTTP client` and `HTTP server`, not just `client` and `server`. If your
project has its own "client" or "agent" concept (e.g. a multi-agent
system), unqualified terms are ambiguous. Always scope.

### `#comment` column for intent

Use `#comment` as a meta column that matchers ignore. Use it to
document *why* a row has the values it does, especially when values
are calculated:

```gherkin
Given session "alpha" has totals:
  | totalTokens | #comment                  |
  | 95          | exceeds 90% of 100 window |
```

No more one-off steps just to convey intent.

### Symmetric Given and Then

If `Then session "X" has transcript matching:` accepts columns like
`type, message.role, message.content`, then `Given session "X" has
transcript:` should accept the same columns. The author of a scenario
shouldn't learn two schemas.

### EDN over JSON in table cells (Clojure projects)

Use `["Once " "upon " "a " "time..."]` (EDN) over
`["Once ","upon ","a ","time..."]` (JSON) for Clojure-native data.
It reads more natively in Clojure step code.

### Async request/response pattern

Every request is fire-and-forget. `When` sends and returns
immediately. `Then the server sends response N:` awaits and matches
by id. No "pending" or "without awaiting" phrasing needed. Matches the
protocol's actual semantics.

### Regex assertions can hide shape bugs

`the output matches` typically runs each row as a regex and passes if the
pattern is found anywhere in the output. That means a `(pr-str ...)`
one-liner passes the same assertions as a fully formatted multi-line
render — the content is there, just all on one line. When the *shape*
matters, add a structural assertion alongside:

```gherkin
Then the output matches:
  | pattern   |
  | :crew     |
  | :defaults |
And the output has at least 5 lines
```

This is a real failure mode — a pretty-print bug shipped because
scenarios only checked that specific keys appeared, never that the
output was multi-line.

### Log assertions on every mutation

Every scenario that performs a mutation or notable lifecycle event
should assert its log entry — `Then the log has entries matching:`.
Missing log assertions let logging silently die; a regression where
a mutation stops emitting info-level events goes unnoticed. Info and
above are spec-worthy; debug traces are not.

Event-keyword convention: `:domain/action` — `:config/set`,
`:session/compaction-started`, `:tool/exec`, `:auth/login`. Keep it
grep-friendly.

### Keep step seams explicit

If reading the step text does not tell you which product path is being
exercised, the step is too smart.

Prefer explicit seams:

- `the user sends "..." on session "..." via in-memory transport`
- `the HTTP client sends request N:`
- `the CLI is run with "..."`

Avoid generic steps that silently decide whether they mean CLI, HTTP,
or some test-only shortcut.

### Separate action from waiting

Don't hide async waiting inside action steps unless the scenario really
needs "send and fully await" semantics as one indivisible behavior.

Prefer:

- `When the user sends "..." on session "..."`
- `Then the turn completes`
- `And the async task completes`

over a single step that sends, polls, captures output, awaits futures,
and splices test state behind the reader's back.

### `reply` must mean reply, not stdout-by-accident

If a scenario says `the reply contains`, the step should read the
channel-visible reply for that seam. It should not piggyback on whatever
buffer currently happens to back `stdout` assertions.

`reply` is a domain concept. `stdout` is a transport-specific detail.
Keep them separate in step state and in step names.

### Avoid implicit config derivation inside steps

Hidden setup is the fastest way to make a feature unreadable. If a step
has to infer crew, model, provider, boot files, current time, and the
channel implementation before it can run, the scenario is omitting too
much of the setup that actually matters.

Prefer explicit setup steps for behavior that depends on:

- agent membership / configuration
- model/provider selection
- channel/transport
- current time
- config/log capture

The goal is not zero helper code; the goal is that the feature text says
enough that a reader can tell what path is under test.

### Boring step definitions are a feature

The best acceptance steps are boring wrappers around real product seams.
They should set up state, call the product, and assert the result — not
quietly compensate for missing product behavior.

If a step starts doing lots of interpretation, inference, reshaping, or
fallback behavior, stop and ask whether the product needs a better seam
instead.

## Common Traps

### Narrative steps for readability

Avoid reaching for English prose because it "reads better." Tables are
clearer if you commit to them. A narrative step hides the data; a table
shows it.

### One-off steps that hide intent

Steps like `the session totalTokens exceeds 90% of the context window`
feel intent-revealing but they proliferate and can't be combined. The
`#comment` column pattern handles this cleanly — don't fall back to
one-offs.

### Forgetting the `@wip` convention

When an existing scenario's expected behavior changes, mark the whole
scenario `@wip` until the implementation catches up. Don't leave
passing-but-wrong scenarios. Failing scenarios ARE the spec for the
next bead.

### Deceptive default fallbacks

If the system silently falls back to a bundled default that looks
like user content, it lies. A `config show` command on a fresh install
that prints a crafted-looking config the user never wrote — a new
operator can't tell what's real vs fabricated. Prefer fail-fast
with guidance ("no config found, create `<path>`") or an explicit
`init` bootstrap, not silent materialization. Applies to any "helpful"
fallback that looks identical to an intentional configuration.

Cover this with a scenario that asserts the absence (or shape) of any
default — don't let a silent fallback ship.

## Anti-Patterns

### Too many steps

If a scenario has more than 5-7 steps, it's doing too much. Split it or create higher-level steps.

### Implementation details in steps

```gherkin
# BAD — exposes implementation
Given a row is inserted into the users table with name "alice"
When a POST request is sent to /api/login with JSON body {"user": "alice"}

# GOOD — business language
Given a user "alice"
When alice logs in
```

### Asserting in Given steps

Given steps set up state. They should never verify anything.

### Incidental details

Only include details that matter to the scenario.

```gherkin
# BAD — irrelevant details
Given a user "alice" with email "alice@example.com" created on "2024-01-15" with role "admin"
When the user logs in
Then the response status should be 200

# GOOD — only what matters
Given a user "alice" with role "admin"
When the user logs in
Then the response status should be 200
```

## Feature File Organization

- One feature per file
- Name the file after the feature: `authentication.feature`, `checkout.feature`
- Group related features in a directory: `features/`
- Keep features close to the code they test

## Checklist before finalizing

- [ ] One behavior per scenario; names describe outcomes
- [ ] Concrete example data (entertaining, never "test value 1")
- [ ] Data in Given (tables for 2+ fields); one high-level When
- [ ] Then steps assert observable results, specifically
- [ ] Tables over narrative; request/response symmetry held
- [ ] Protocol noise stripped, semantic anchors kept
- [ ] Outlines only where data variation is the point
- [ ] Descriptive feature file names (order-checkout.feature, not test1.feature)
