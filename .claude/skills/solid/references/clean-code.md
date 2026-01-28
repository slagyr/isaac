# Clean Code Practices for Clojure

## What is Clean Code?

Code that is:
- **Easy to understand** - reveals intent clearly
- **Easy to change** - modifications are localized
- **Easy to test** - pure functions, injectable dependencies
- **Simple** - no unnecessary complexity

## The Human-Centered Approach

Code has THREE consumers:
1. **Users** - get their needs met
2. **Customers** - make or save money
3. **Developers** - must maintain it

Design for all three, but remember: **developers read code 10x more than they write it.**

## Clojure Naming Conventions

### Basic Conventions
- **kebab-case** for functions and vars: `calculate-total`, `user-name`
- **Predicates** end with `?`: `valid?`, `empty?`, `premium-user?`
- **Dangerous/side-effecting** ops end with bang: save!, reset!, delete!
- **Conversion functions** use `->`: `map->User`, `str->int`, `request->user`
- **Private functions** use `defn-`: `(defn- helper-fn [x] ...)`

### Naming Principles (in order of priority)

#### 1. Consistency & Uniqueness (HIGHEST PRIORITY)
Same concept = same name everywhere. One name per concept.

```clojure
;; BAD: Inconsistent names for same concept
(defn get-user-by-id [id] ...)
(defn fetch-customer-by-id [id] ...)
(defn retrieve-client-by-id [id] ...)

;; GOOD: Consistent
(defn user [id] ...)
(defn order [id] ...)
(defn product [id] ...)
```

#### 2. Understandability
Use domain language, not technical jargon.

```clojure
;; BAD: Technical
(def arr (filter #(:active %) users))

;; GOOD: Domain language
(def active-customers (filter :active users))
```

#### 3. Specificity
Avoid vague names: `data`, `info`, `manager`, `handler`, `processor`, `utils`

```clojure
;; BAD: Vague
(defn process-data [data] ...)
(def info (get-info))

;; GOOD: Specific
(defn validate-payment [payment] ...)
(def order-summary (calculate-summary order))
```

#### 4. Brevity (but not at cost of clarity)
Short names are good only if meaning is preserved.

```clojure
;; BAD: Too cryptic
(def usr-lst (get-usrs))

;; BAD: Unnecessarily long
(def list-of-all-active-users-in-the-system (get-active-users))

;; GOOD: Brief but clear
(def active-users (get-active-users))
```

#### 5. Searchability
Names should be unique enough to grep/search.

```clojure
;; BAD: Common word, hard to search
(def data (fetch))

;; GOOD: Unique, searchable
(def order-summary (fetch-order-summary))
```

---

## Clojure-Specific Clean Code Rules

### 1. Use Threading Macros to Flatten Nesting

Threading macros (`->`, `->>`, `some->`, `cond->`) make code read linearly.

```clojure
;; BAD: Nested calls
(save-to-db
  (calculate-tax
    (apply-discount
      (validate-order order))))

;; GOOD: Threading macro
(-> order
    validate-order
    apply-discount
    calculate-tax
    save-to-db!)

;; Use ->> when the argument goes last
(->> orders
     (filter :active)
     (map calculate-total)
     (reduce +))

;; Use some-> for nil-safe threading
(some-> user
        :address
        :city
        str/upper-case)
```

### 2. Prefer `when` Over Single-Branch `if`

```clojure
;; BAD: if with nil else
(if (valid? order)
  (process! order)
  nil)

;; GOOD: when
(when (valid? order)
  (process! order))
```

### 3. Use `if-let` and `when-let` for Conditional Binding

```clojure
;; BAD: Separate let and if
(let [user (get-user id)]
  (if user
    (send-email! user)
    (log-error "User not found")))

;; GOOD: Combined
(if-let [user (get-user id)]
  (send-email! user)
  (log-error "User not found"))

;; When you don't need else
(when-let [user (get-user id)]
  (send-email! user))
```

### 4. Use `cond` for Multiple Conditions

```clojure
;; BAD: Nested ifs
(if (> age 65)
  :senior
  (if (> age 18)
    :adult
    (if (> age 12)
      :teen
      :child)))

;; GOOD: cond
(cond
  (> age 65) :senior
  (> age 18) :adult
  (> age 12) :teen
  :else      :child)

;; Use condp for repeated predicate
(condp = status
  :pending   (process-pending order)
  :confirmed (ship-order order)
  :shipped   (track-order order)
  (throw (ex-info "Unknown status" {:status status})))
```

### 5. Wrap Primitives in Domain Types

Primitives should be wrapped when they have domain meaning.

```clojure
;; BAD: Primitive obsession
(defn create-user [email age zip-code] ...)
;; Easy to pass wrong values, no validation

;; GOOD: Domain types with schema
(require '[c3kit.apron.schema :as schema])

(def user-schema
  {:email {:type :string :validate #(re-matches #".+@.+\..+" %)}
   :age {:type :int :validate #(<= 0 % 150)})
   :zip-code {:type :string :validate #(re-matches #"\d{5}" %)}})

(defn create-user [user-data]
  (schema/conform! user-schema user-data)
  ...)
```

### 6. Keep Functions Small (< 10 lines)

```clojure
;; BAD: Long function doing multiple things
(defn process-order [order]
  ;; Validation
  (when-not (:items order)
    (throw (ex-info "Empty order" {})))
  (when-not (:customer order)
    (throw (ex-info "No customer" {})))
  ;; Calculate totals
  (let [subtotal (reduce + (map :price (:items order)))
        tax (* subtotal 0.1)
        total (+ subtotal tax)]
    ;; Save
    (jdbc/insert! db :orders (assoc order :total total))
    ;; Notify
    (send-email (:customer-email order) "Order confirmed")
    ;; Return
    {:order-id (:id order) :total total}))

;; GOOD: Composed small functions
(defn validate-order [order]
  (when-not (:items order)
    (throw (ex-info "Empty order" {})))
  (when-not (:customer order)
    (throw (ex-info "No customer" {})))
  order)

(defn calculate-totals [order]
  (let [subtotal (->> order :items (map :price) (reduce +))
        tax (* subtotal 0.1)]
    (assoc order :subtotal subtotal :tax tax :total (+ subtotal tax))))

(defn process-order! [deps order]
  (let [order (-> order validate-order calculate-totals)]
    ((:save-order! deps) order)
    ((:send-confirmation! deps) order)
    {:order-id (:id order) :total (:total order)}))
```

### 7. Use Destructuring Wisely

```clojure
;; Destructure in function arguments
(defn greet [{:keys [first-name last-name]}]
  (str "Hello, " first-name " " last-name))

;; With defaults
(defn connect [{:keys [host port timeout]
                :or {host "localhost"
                     port 8080
                     timeout 5000}}]
  ...)

;; Nested destructuring (use sparingly)
(defn shipping-city [{{:keys [city]} :address}]
  city)

;; Sequential destructuring
(defn process [[head & tail]]
  (when head
    (handle head)
    (recur tail)))
```

### 8. Namespace Organization

```clojure
;; GOOD: Clear, organized requires
(ns myapp.orders.service
  (:require
    [clojure.string :as str]
    [c3kit.apron.schema :as schema]
    [myapp.orders.pricing :as pricing]
    [myapp.orders.db :as db]
    [myapp.notifications.email :as email]))

;; Group by: stdlib, external libs, internal namespaces
```

---

## Comments

### When to Write Comments

**Only write comments to explain WHY, not WHAT or HOW.**

Code explains what and how. Comments explain business reasons, non-obvious decisions, or warnings.

```clojure
;; BAD: Explains what (redundant)
;; Increment counter by 1
(inc counter)

;; GOOD: Explains why
;; Compensate for 0-based indexing in legacy API
(inc counter)

;; GOOD: Warning
;; WARNING: Order matters - auth must come before logging
(-> handler
    wrap-auth
    wrap-logging)
```

### Prefer Self-Documenting Code

Instead of commenting, rename to make intent clear.

```clojure
;; BAD: Comment needed
;; Check if user can access premium features
(and (>= (:subscription-level user) 2)
     (not (:banned? user)))

;; GOOD: Self-documenting
(defn can-access-premium? [user]
  (and (>= (:subscription-level user) 2)
       (not (:banned? user))))

(can-access-premium? user)
```

---

## Formatting

### Vertical Spacing
- Related code together
- Blank lines between logical sections
- Public functions before private helpers

### Indentation
- 2 spaces (Clojure standard)
- Align arguments vertically when spanning lines

```clojure
;; GOOD: Aligned arguments
(defn complex-function
  [arg1
   arg2
   {:keys [opt1 opt2]}]
  ...)

;; GOOD: Threading on separate lines
(-> order
    validate
    calculate-tax
    save!)
```

### Max Line Length
- Aim for ~80-100 characters
- Break long forms across lines

```clojure
;; BAD: Too long
(defn process [x] (if (and (valid? x) (authorized? x) (not-expired? x)) (handle x) (reject x)))

;; GOOD: Broken across lines
(defn process [x]
  (if (and (valid? x)
           (authorized? x)
           (not-expired? x))
    (handle x)
    (reject x)))
```

---

## Clojure Idioms Summary

| Instead of... | Use... |
|---------------|--------|
| Nested function calls | Threading macros (`->`, `->>`) |
| `if` with `nil` else | `when` |
| Separate `let` + `if` | `if-let`, `when-let` |
| Nested `if`s | `cond`, `condp`, `case` |
| Mutable variables | `let` bindings, `loop/recur` |
| Raw primitives | Schema, records, validated constructors |
| Long functions | Small, composable functions |
| Comments explaining code | Better names, extracted functions |

---

## Anti-Patterns to Avoid

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| Deep nesting | Hard to read | Threading macros, extract functions |
| Primitive obsession | No validation, easy to confuse | Schema, records |
| God namespace | Does everything | Split by responsibility |
| Side effects everywhere | Hard to test, reason about | Isolate to bang functions |
| Abbreviations | Cryptic code | Full, clear names |
| Magic values | Unclear meaning | Named constants, keywords |
