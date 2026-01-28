# Managing Complexity

## The Two Types of Complexity

### Essential Complexity
Inherent to the problem domain. Cannot be removed, only managed.
- Business rules
- Domain logic
- User requirements

### Accidental Complexity
Introduced by our solutions. CAN and SHOULD be minimized.
- Poor abstractions
- Unnecessary indirection
- Framework ceremony
- Technical debt
- Mutable state (Clojure helps here!)

**Goal: Minimize accidental complexity while clearly expressing essential complexity.**

---

## Detecting Complexity

### 1. Change Amplification
Small changes require touching many files.

**Symptom:** "To add this field, I need to update 15 namespaces."

**Cause:** Scattered responsibilities, poor namespace boundaries.

### 2. Cognitive Load
Code is hard to understand, requires holding too much in memory.

**Symptom:** "I need to understand 10 other namespaces to understand this one."

**Cause:** Tight coupling, hidden dependencies, unclear naming.

### 3. Unknown Unknowns
Behavior is surprising, side effects are hidden.

**Symptom:** "I changed this, and something completely unrelated broke."

**Cause:** Global state, hidden side effects, implicit contracts.

---

## The XP Values for Fighting Complexity

From Extreme Programming:

### 1. Communication
Code should communicate clearly. Names, structure, tests all contribute.

### 2. Simplicity
Do the simplest thing that could possibly work.

### 3. Feedback
Fast feedback loops catch complexity early. TDD, REPL, code review.

### 4. Courage
Refactor aggressively. Don't let complexity accumulate.

### 5. Respect
Respect future readers (including yourself). Write for humans first.

---

## KISS - Keep It Simple, Silly

> "The simplest solution that works is usually the best."

### How to Apply:
1. Start with the obvious solution
2. Only add complexity when REQUIRED
3. Prefer boring, well-understood approaches
4. Question every abstraction

```clojure
;; Over-engineered
(defprotocol UserServiceFactory
  (create-service [this config]))

(defprotocol UserService
  (get-user [this id])
  (save-user [this user]))

(defrecord DefaultUserServiceFactory []
  UserServiceFactory
  (create-service [_ config]
    (->DefaultUserService (:db config))))

(defrecord DefaultUserService [db]
  UserService
  (get-user [_ id] ...)
  (save-user [_ user] ...))

;; KISS - just functions!
(defn get-user [db id]
  (jdbc/get-by-id db :users id))

(defn save-user! [db user]
  (jdbc/insert! db :users user))
```

---

## YAGNI - You Aren't Gonna Need It

> "Don't build features until they're actually needed."

### Warning Signs:
- "We might need this later"
- "It would be nice to have"
- "Just in case"
- "For future extensibility"

### The Cost of YAGNI Violations:
1. **Development time** - Building unused features
2. **Maintenance burden** - Code that must be maintained
3. **Cognitive load** - More to understand
4. **Wrong abstraction** - Guessing future needs incorrectly

```clojure
;; YAGNI violation: Building for hypothetical needs
(defrecord User
  [id
   name
   email
   ;; "We might need these someday"
   middle-name
   secondary-email
   fax-number
   linkedin-profile
   twitter-handle
   instagram-handle
   preferred-contact-method
   timezone
   locale])

;; YAGNI: Only what's needed NOW
(defn make-user [name email]
  {:id (random-uuid)
   :name name
   :email email})
```

---

## DRY - Don't Repeat Yourself (with The Rule of Three)

> "Every piece of knowledge should have a single, unambiguous representation."

### BUT: The Rule of Three

**Don't extract duplication until you see it THREE times.**

Why? The wrong abstraction is worse than duplication.

```
Duplication #1 → Leave it
Duplication #2 → Note it, leave it
Duplication #3 → NOW extract it
```

### Example:
```clojure
;; First time - leave it
(defn process-user-order! [order]
  (validate-order! order)
  (calculate-tax order)
  (save-order! order))

;; Second time - note the similarity, but leave it
(defn process-guest-order! [order]
  (validate-order! order)
  (calculate-tax order)
  (save-order! order)
  (send-guest-email! order))

;; Third time - NOW extract
(defn process-corporate-order! [order]
  (validate-order! order)
  (calculate-tax order)
  (save-order! order)
  (apply-corporate-discount! order))

;; After three, extract the common parts
(defn- process-order-base! [order]
  (validate-order! order)
  (let [order (calculate-tax order)]
    (save-order! order)
    order))

(defn process-user-order! [order]
  (process-order-base! order))

(defn process-guest-order! [order]
  (let [order (process-order-base! order)]
    (send-guest-email! order)))

(defn process-corporate-order! [order]
  (let [order (process-order-base! order)]
    (apply-corporate-discount! order)))
```

---

## Separation of Concerns

> "Each namespace/function should address a single concern."

### Concerns to Separate:
- **Business logic** vs **Infrastructure**
- **What** (policy) vs **How** (mechanism)
- **Pure** vs **Effectful**
- **Data** vs **Behavior**

### Example:
```clojure
;; BAD: Mixed concerns
(defn process-order! [order]
  ;; Validation
  (when-not (:items order)
    (throw (ex-info "Empty" {})))

  ;; Business logic
  (let [total (->> order :items (map :price) (reduce +))]

    ;; Persistence
    (jdbc/insert! db :orders (assoc order :total total))

    ;; Notification
    (http/post "https://email-api.com/send"
               {:to (:email order) :subject "Confirmed"})))

;; GOOD: Separated concerns
(ns myapp.orders.validation)
(defn validate! [order]
  (when-not (:items order)
    (throw (ex-info "Empty order" {})))
  order)

(ns myapp.orders.pricing)
(defn calculate-total [order]
  (->> order :items (map :price) (reduce +)))

(ns myapp.orders.db)
(defn save! [db order]
  (jdbc/insert! db :orders order))

(ns myapp.notifications.email)
(defn send-confirmation! [email-client order]
  (send! email-client (:email order) "Order confirmed"))

(ns myapp.orders.service)
(defn process-order! [{:keys [db email-client]} order]
  (-> order
      validation/validate!
      (as-> o (assoc o :total (pricing/calculate-total o)))
      (->> (db/save! db)))
  (email/send-confirmation! email-client order))
```

---

## Clojure's Complexity Reducers

### 1. Immutability by Default
No wondering "who changed this?" - data doesn't change.

### 2. Pure Functions
No hidden side effects. Same input → same output.

### 3. Explicit State
When you need state, `atom`/`ref`/`agent` make it explicit.

### 4. Data Orientation
Plain data is simpler than object hierarchies.

### 5. REPL-Driven Development
Immediate feedback catches complexity early.

```clojure
;; Pure function - easy to understand and test
(defn calculate-discount [user order]
  (cond
    (:premium? user) (* (:total order) 0.2)
    (> (:total order) 100) (* (:total order) 0.1)
    :else 0))

;; State is explicit
(def app-state (atom {:users {} :orders []}))

;; Changes are tracked
(add-watch app-state :logger
  (fn [_ _ old new]
    (println "State changed")))
```

---

## Managing Technical Debt

### Types of Technical Debt:
1. **Deliberate** - Conscious trade-off for speed
2. **Accidental** - Mistakes, lack of knowledge
3. **Bit rot** - Code degrades over time

### The Boy Scout Rule:
> "Leave the code better than you found it."

Every time you touch code:
- Improve one small thing
- Fix one naming issue
- Extract one function
- Add one missing test

### When to Pay Down Debt:
- When it's in your path (you're already there)
- When it's blocking new features
- When it's causing bugs
- During dedicated refactoring time

### When NOT to Refactor:
- Code that works and won't change
- Code being replaced soon
- When you don't have tests

---

## The Four Elements of Simple Design

In priority order (from XP):

1. **Runs all the tests**
   - If it doesn't work, nothing else matters

2. **Expresses intent**
   - Clear names, obvious structure
   - Code tells the story

3. **No duplication**
   - DRY (but Rule of Three)
   - Single source of truth

4. **Minimal**
   - Fewest namespaces and functions possible
   - Remove anything unnecessary

If these four are true, the design is simple enough.

---

## Complexity Budget

Every feature has a complexity cost. Ask:

1. **Is this feature worth its complexity?**
2. **Is there a simpler way to achieve the same goal?**
3. **What's the maintenance cost over time?**

```clojure
;; High complexity cost - custom DSL
(defmacro defworkflow [name steps]
  `(def ~name
     (->Workflow
       ~(mapv (fn [s] `(->Step ~@s)) steps))))

;; Low complexity cost - plain data + functions
(def my-workflow
  {:steps [{:name :validate :fn validate}
           {:name :process :fn process}
           {:name :notify :fn notify}]})

(defn run-workflow [workflow data]
  (reduce (fn [d step] ((:fn step) d))
          data
          (:steps workflow)))
```

Prefer the low-complexity option unless there's a compelling reason.
