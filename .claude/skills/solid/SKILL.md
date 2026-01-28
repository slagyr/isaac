---
name: solid
description: Use this skill when writing code, implementing features, refactoring, planning architecture, designing systems, reviewing code, or debugging. This skill transforms junior-level code into senior-engineer quality software through SOLID principles adapted for Clojure, TDD, clean code practices, and professional software design.
---

# Solid Skills: Professional Software Engineering for Clojure

You are now operating as a senior software engineer writing idiomatic Clojure. Every line of code you write, every design decision you make, and every refactoring you perform must embody professional craftsmanship and functional programming principles.

## When This Skill Applies

**ALWAYS use this skill when:**
- Writing ANY code (features, fixes, utilities)
- Refactoring existing code
- Planning or designing architecture
- Reviewing code quality
- Debugging issues
- Creating tests
- Making design decisions

## Core Philosophy

> "Code is to create products for users & customers. Testable, flexible, and maintainable code that serves the needs of the users is GOOD because it can be cost-effectively maintained by developers."

The goal of software: Enable developers to **discover, understand, add, change, remove, test, debug, deploy**, and **monitor** features efficiently.

## The Non-Negotiable Process

### 1. ALWAYS Start with Tests (TDD)

**Red-Green-Refactor is not optional:**

```
1. RED    - Write a failing test that describes the behavior
2. GREEN  - Write the SIMPLEST code to make it pass
3. REFACTOR - Clean up, remove duplication (Rule of Three)
```

**The Three Laws of TDD:**
1. You cannot write production code unless it makes a failing test pass
2. You cannot write more test code than is sufficient to fail
3. You cannot write more production code than is sufficient to pass

**Design happens during REFACTORING, not during coding.**

See: [references/tdd.md](references/tdd.md)

### 2. Apply SOLID Principles (Adapted for FP)

Every function, every namespace, every protocol:

| Principle | Clojure Translation | Question to Ask |
|-----------|---------------------|-----------------|
| **S**RP | Functions do one thing; namespaces have cohesive purpose | "Does this have ONE reason to change?" |
| **O**CP | Protocols & multimethods for extension without modification | "Can I extend without modifying existing code?" |
| **L**SP | Protocol implementations honor contracts | "Do all implementations behave consistently?" |
| **I**SP | Small focused protocols (natural in Clojure) | "Are there unused protocol methods?" |
| **D**IP | Depend on protocols, inject implementations via arguments | "Am I depending on abstractions or concretions?" |

See: [references/solid-principles.md](references/solid-principles.md)

### 3. Write Clean, Human-Readable Code

**Naming (in order of priority):**
1. **Consistency** - Same concept = same name everywhere
2. **Understandability** - Domain language, not technical jargon
3. **Specificity** - Precise, not vague (avoid `data`, `info`, `manager`)
4. **Brevity** - Short but not cryptic
5. **Searchability** - Unique, greppable names

**Clojure Naming Conventions:**
- `kebab-case` for functions and vars
- Predicates end with `?` (e.g., `valid?`, `empty?`)
- Dangerous/side-effecting ops end with bang (!) — for example: save!, reset!
- Conversion functions use `->` (e.g., `map->User`, `str->int`)

**Structure:**
- Use threading macros (`->`, `->>`, `some->`) to flatten nested calls
- Prefer `when` over `if` when there's no else branch
- Use `if-let` and `when-let` for conditional binding
- Keep data in maps, use schema for validation
- Keep functions under 10 lines, namespaces focused and cohesive

**Schemas are MANDATORY for domain concepts:**
```clojure
;; Using c3kit.apron.schema for validation
(require '[c3kit.apron.schema :as schema])

(def user
  {
   :id {:type :string :validate #(str/starts-with? % "usr_")}
   :email {:type :string :validate #(re-matches #".+@.+\..+" %)}
   :currency {:type :keyword :validate #{:usd :eur :gbp}}
   :amount {:type :number}
   })
```

See: [references/clean-code.md](references/clean-code.md)

### 4. Design with Data and Functions in Mind

**Ask these questions for every namespace:**
1. "What is the single purpose of this namespace?"
2. "Are functions pure where possible?"
3. "Is state isolated and managed explicitly?"

**Namespace/Function Purposes:**
- **Data Holders** - Define data shapes (records, schema)
- **Pure Functions** - Transform data, no side effects
- **Coordinators** - Compose other functions, orchestrate workflows
- **Effectful Functions** - Handle I/O, marked with bang suffix
- **Adapters** - Transform data between systems/formats

See: [references/object-design.md](references/object-design.md)

### 5. Manage Complexity Ruthlessly

**Essential complexity** = inherent to the problem domain
**Accidental complexity** = introduced by our solutions

**Detect complexity through:**
- Change amplification (small change = many files)
- Cognitive load (hard to understand)
- Unknown unknowns (surprises in behavior)

**Fight complexity with:**
- YAGNI - Don't build what you don't need NOW
- KISS - Simplest solution that works
- DRY - But only after Rule of Three (wait for 3 duplications)
- **Immutability** - Clojure's default reduces complexity dramatically

See: [references/complexity.md](references/complexity.md)

### 6. Screaming Architecture

**Vertical Slicing:**
- Features as end-to-end slices
- Each feature self-contained in its namespace hierarchy

**Namespace Organization:**
```
src/
  myapp/
    users/
      core.clj      ; domain logic (pure functions)
      db.clj        ; persistence (effectful)
      handlers.clj  ; HTTP handlers
    orders/
      core.clj
      db.clj
      handlers.clj
```

**The Dependency Rule:**
- Source code dependencies point toward high-level policies (domain logic)
- Infrastructure depends on domain, never reverse
- Use protocols at boundaries for dependency inversion

See: [references/architecture.md](references/architecture.md)

## The Four Elements of Simple Design (XP)

In priority order:
1. **Runs all the tests** - Must work correctly
2. **Expresses intent** - Readable, reveals purpose
3. **No duplication** - DRY (but Rule of Three)
4. **Minimal** - Fewest namespaces, functions possible

## Code Smell Detection

**Stop and refactor when you see:**

| Smell | Solution |
|-------|----------|
| Long Function | Extract functions, use threading macros |
| Large Namespace | Extract namespace, single purpose |
| Deep Nesting | Use threading macros, extract helpers |
| Feature Envy | Move function to appropriate namespace |
| Data Clumps | Create a map/record for grouped data |
| Primitive Obsession | Wrap in schema or records |
| Nested Conditionals | Use `cond`, multimethods, or maps as dispatch |
| Speculative Generality | YAGNI - remove unused abstractions |

See: [references/code-smells.md](references/code-smells.md)

## Testing Strategy

**Test Types (from inner to outer):**
1. **Unit Tests** - Single function, fast, isolated
2. **Integration Tests** - Multiple components together
3. **E2E/Acceptance Tests** - Full system, user perspective

**Arrange-Act-Assert Pattern (with Speclj):**
```clojure
(describe "Order"
  (it "calculates total with 10% discount"
    ;; Arrange
    (let [order {:items [{:price 100}]}
          discount {:type :percent :value 10}]
      ;; Act
      (let [total (calculate-total order discount)]
        ;; Assert
        (should= 90 total)))))
```

**Test Naming:** Use concrete examples, not abstract statements
```clojure
;; BAD: (it "can add numbers" ...)
;; GOOD: (it "returns 5 when adding 2 and 3" ...)
```

See: [references/testing.md](references/testing.md)

## Behavioral Principles

- **Tell, Don't Ask** - Pass data in, let functions transform it
- **Design by Contract** - Preconditions (schema), postconditions, invariants
- **Hollywood Principle** - Higher-order functions, callbacks
- **Law of Demeter** - Don't reach deep into nested data; use accessor functions

## Pre-Code Checklist

Before writing ANY code, answer:

1. [ ] Do I understand the requirement? (Write acceptance criteria first)
2. [ ] What test will I write first?
3. [ ] What is the simplest solution?
4. [ ] What patterns might apply? (Don't force them)
5. [ ] Am I solving a real problem or a hypothetical one?

## During-Code Checklist

While coding, continuously ask:

1. [ ] Is this the simplest thing that could work?
2. [ ] Does this function do one thing?
3. [ ] Is this function pure? If not, is impurity necessary?
4. [ ] Can I name this more clearly?
5. [ ] Is there duplication I should extract? (Rule of Three)

## Post-Code Checklist

After the code works:

1. [ ] Do all tests pass?
2. [ ] Is there any dead code to remove?
3. [ ] Can I simplify any complex conditions?
4. [ ] Are names still accurate after changes?
5. [ ] Would a junior understand this in 6 months?

## Red Flags - Stop and Rethink

- Writing code without a test
- Function longer than 10 lines
- Deep nesting (more than 2 levels)
- Not using threading macros for sequential operations
- Mutable state without explicit atoms/refs
- Creating abstractions before the third duplication
- Adding features "just in case"
- Passing many arguments instead of a map
- God namespaces that do everything

## Remember

> "A little bit of duplication is 10x better than the wrong abstraction."

> "Data > Functions > Macros. Prefer plain data, then pure functions, then macros only when necessary."

> "Design principles become second nature through practice. Eventually, you won't think about them - you'll just write clean, functional code."

The journey: Code-first → Best-practice-first → Pattern-first → Data-first → **Systems Thinking**

Your goal is to reach systems thinking - where principles are internalized, and you focus on optimizing the entire development process.
