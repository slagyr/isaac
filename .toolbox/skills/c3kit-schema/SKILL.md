---
name: c3kit-schema
description: Use this skill when working with c3kit.apron.schema — defining schemas, calling coerce/validate/conform/present, handling errors, or writing custom spec functions. Covers the full API including nested types, entity-level specs, and error handling.
---

# c3kit.apron.schema

Full reference: https://raw.githubusercontent.com/cleancoders/c3kit-apron/refs/heads/master/SCHEMA.md

## The Silent Failure Trap

**Non-bang functions never throw. They return the entity with error objects embedded in failed fields.**

If you call a non-bang function and ignore the return value, errors vanish silently.

```clojure
;; BAD — errors silently discarded
(schema/conform user-schema raw-input)
(save! raw-input)

;; GOOD — use bang to throw on failure
(let [user (schema/conform! user-schema raw-input)]
  (save! user))

;; GOOD — use non-bang when you want to handle errors explicitly
(let [result (schema/conform user-schema raw-input)]
  (if (schema/error? result)
    (render-errors (schema/message-map result))
    (save! result)))
```

Never call `schema/coerce`, `schema/validate`, `schema/conform`, or `schema/present` and ignore the return value.

## The Four Operations

| Operation | Purpose | Non-bang | Bang |
|---|---|---|---|
| **coerce** | Convert raw values to typed values | `schema/coerce` | `schema/coerce!` |
| **validate** | Check values against rules | `schema/validate` | `schema/validate!` |
| **conform** | Coerce then validate | `schema/conform` | `schema/conform!` |
| **present** | Transform for display/output | `schema/present` | `schema/present!` |

**Prefer `conform!`** for config and boundary validation where invalid input should fail fast.
Use non-bang only when you intend to handle errors in the return value.

## Defining a Schema

A schema is a plain map. Each key maps to a spec.

```clojure
(def user
  {:kind  (schema/kind :user)   ;; enforces :kind value
   :name  {:type :string :validate :present :message "is required"}
   :email {:type :string :validate :email}
   :age   {:type :int}})
```

### Spec Keys

| Key | Purpose |
|---|---|
| `:type` | Type for coercion and type-validation (see Types below) |
| `:message` | Default error message for coerce and validate failures |
| `:coerce` | Function or list of functions `(fn [v] ...)` → coerced value |
| `:validate` | Function or list of functions `(fn [v] ...)` → truthy/falsy |
| `:validations` | List of ref entries — registered ref names, factory vectors `[:ref & args]`, or `{:validate fn :message "..."}` maps (see [[refs]]) |
| `:coercions` | Same shape as `:validations`, applied as coercions (see [[refs]]) |
| `:present` | Single function `(fn [v] ...)` → presentable value (no list) |
| `:value` | Exact expected value (used by `schema/kind`) |

`:validate`/`:coerce` are function-only; `:validations`/`:coercions` accept the ref registry so schemas can live entirely as EDN data.

### Multiple Validations with Per-Rule Messages

```clojure
{:type        :int
 :message     "must be an int"           ;; used for type-validation failure
 :validations [{:validate even? :message "must be even"}
               {:validate #(<= 0 % 100) :message "out of range"}]}
```

Type-validation runs first. If it fails, other validations are skipped.

## Refs

`:validations` and `:coercions` entries are looked up in the **ref registry** (`c3kit.apron.schema.refs`). The registry is empty by default; call `(refs/install!)` once at startup to load the standard catalog.

Entries can be:
- a registered ref name — `:pos?`, `:email?`, `:trim`
- a factory invocation — `[:between 0 10]`, `[:max-length 3]`, `[:default 99]`
- a map with an override — `{:validate :present? :message "Name is mandatory"}`

```clojure
{:type :string
 :validations [:present? [:max-length 80]]
 :coercions   [:trim]}
```

Standard catalog (abridged): type/numeric predicates (`:string?`, `:pos?`, …), apron predicates (`:present?`, `:email?`, …), comparison/shape factories (`:>`, `:between`, `:matches`, `:one-of`), string/type coercers (`:trim`, `:->int`, `:->keyword`, …), `:default`.

### Combinator refs (2.8.0)

Compose other refs (or inline fns): `:nil-or?`, `:not?`, `:and?`, `:or?`.

```clojure
[:nil-or? :pos?]                       ;; pass nil or any positive
[:and? :integer? [:between 0 10]]
[:or?  :pos? :zero?]
[:not? :pos?]
```

Custom combinators resolve their args via `schema/->validate-fn` / `schema/->coerce-fn`:

```clojure
(schema/register-ref! :every-of?
  (fn [pred-ref]
    (let [pred (schema/->validate-fn pred-ref)]
      {:validate (fn [coll] (every? pred coll))})))
```

Combinators are value-scoped only — they do not compose entity-scoped refs.

### Registering refs

```clojure
(schema/register-ref! :app/non-empty-vec
                      {:validate (every-pred vector? seq)
                       :message  "must be a non-empty vector"})

(schema/register-ref! :app/clamp                       ;; factory
                      (fn [lo hi]
                        {:coerce  #(max lo (min hi %))
                         :message (str "clamped to [" lo ", " hi "]")}))
```

`schema/verify-schema-refs` walks a schema and throws on any unresolved or wrong-slot ref — call once after registration to fail fast. `schema/*ref-registry*` is a bindable dynamic var for test/plugin isolation.

## Entity-Level Specs

Use `:*` for cross-field rules. All functions receive the whole entity.

```clojure
(def order
  {:kind   (schema/kind :order)
   :total  {:type :double}
   :tax    {:type :double}
   :*      {:amount-due {:coerce #(+ (:total %) (:tax %))
                         :validate pos?
                         :message "must be positive"}}})
```

Entity-level specs can add new computed fields or validate existing ones.

### Entity-scoped refs (2.8.0)

Alternative to `:*` when the cross-field rule belongs *next to* the field it constrains. A ref with `:scope :entity` receives `(entity field-key)` instead of `(value)`:

```clojure
(schema/register-ref! :required-when
  (fn [other-key expected]
    {:validate (fn [entity field-key]
                 (or (not= expected (get entity other-key))
                     (schema/present? (get entity field-key))))
     :scope    :entity
     :message  (str "is required when " other-key " is " expected)}))

(def pet
  {:species     {:type :string}
   :tail-length {:type :int :validations [[:required-when :species "dog"]]}})
```

Works the same way under `:coercions` for sibling-derived values:

```clojure
(schema/register-ref! :full-name
                      {:coerce (fn [e _k] (str (:first e) " " (:last e)))
                       :scope  :entity})
```

**Pipeline order** for full-entity ops (`validate` / `coerce` / `conform`):
1. Per-field, value-scoped: coerce → validate
2. Per-field, entity-scoped: coerce → validate (after step 1 finishes for every field)
3. `:*` entity-level: coerce → validate

`validate-value!`, `coerce-value!`, `conform-value!` operate on a single value — entity-scoped entries are silently bypassed there.

Inside a nested `:type :map :schema {...}`, the inner field's "entity" is the inner map. Recursion handles it automatically.

## Nested Structures

### Nested map

```clojure
(def line {:kind  (schema/kind :line)
           :start {:type :map :schema point}
           :end   {:type :map :schema point}})
```

### Sequence of typed values

```clojure
(def polygon {:kind   (schema/kind :polygon)
              :points {:type :seq
                       :spec {:type :map :schema point}
                       :validations [{:validate #(>= (count %) 3) :message "need at least 3 points"}]}})
```

All values in a seq must conform to the same spec.

### One of several schemas

```clojure
{:type :one-of :specs [{:type :map :schema point}
                       {:type :map :schema circle}]}
```

`schema` tries each spec in order; uses the first that succeeds.

## Handling Errors

```clojure
;; Check for any error
(schema/error? result)   ;; => true/false

;; Get flat list of messages
(schema/message-seq result)
;; => ["start.x can't coerce \"blah\" to int"]

;; Get nested map of messages (most common for UIs)
(schema/message-map result)
;; => {:start {:x "can't coerce \"blah\" to int"}}

;; Shortcuts — conform/coerce/validate + message-map in one call
(schema/conform-message-map schema entity)   ;; => nil or error map
(schema/coerce-message-map schema entity)
(schema/validate-message-map schema entity)
```

## Good to Know

**Unspecified fields are dropped** — any key not in the schema is removed by schema operations.

**Merging schemas** — use `schema/merge-schemas` to add context-specific validations without duplicating:

```clojure
(schema/merge-schemas base-schema {:email {:validate :unique-email :message "already taken"}})
```

**Validate your schema** — use `schema/conform-schema!` to catch malformed spec definitions early.

**Shorthands** — schema accepts abbreviated specs (e.g. `{:type [point]}` for a seq of points) and expands them on the fly. Prefer explicit forms for clarity.

## Types

```clojure
:any        ;; no type-coercion or type-validation
:bigdec     ;; BigDecimal in clj, number in cljs
:boolean
:date       ;; java.sql.Date in clj — sql databases only
:double     ;; number in cljs
:float      ;; number in cljs
:fn         ;; implements iFn
:ignore     ;; same as :any
:instant    ;; java.util.Date in clj, js/Date in cljs
:int
:keyword
:kw-ref     ;; same as :keyword, Datomic ident
:long
:map        ;; must also specify :schema
:one-of     ;; must also specify :specs
:ref        ;; same as :int, Datomic reference
:seq        ;; must also specify :spec
:string
:timestamp  ;; java.sql.Timestamp in clj — sql databases only
:uri        ;; java.net.URI in clj, string in cljs
:uuid       ;; java.util.UUID in clj, cljs.core/UUID in cljs
```
