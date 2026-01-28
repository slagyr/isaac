# Data-Driven Design in Clojure

## From Objects to Data and Functions

In OOP, objects combine data and behavior. In Clojure, we separate them:
- **Data** - Plain maps, records, schema
- **Functions** - Pure transformations of data
- **Effects** - Isolated at the edges

This separation makes code easier to test, reason about, and compose.

## OOP Concepts → Clojure Approach

| OOP Concept | Clojure Approach |
|-------------|------------------|
| Class | Record + protocol, or plain maps |
| Interface | Protocol |
| Inheritance | Protocol extension, multimethods |
| Value Object | Map/record with schema validation |
| Entity | Map/record with `:id` field |
| Encapsulation | Private fns (`defn-`), namespace boundaries |
| Getters/Setters | Direct access (data is immutable) |

---

## Data-Driven Design

The key insight: **Design around data shapes, not object behaviors.**

### Finding Data Shapes

Start with:
1. **Nouns** in requirements → data structures (maps, records)
2. **Verbs** → pure functions that transform data
3. **Domain concepts** → schema for validation

### Namespace/Function Purposes

Each namespace should have a clear purpose. Common patterns:

| Purpose | Description | Example |
|---------|-------------|---------|
| **Data Definitions** | Define data shapes (records, schema) | `myapp.users.schema` |
| **Pure Functions** | Transform data, no side effects | `myapp.orders.pricing` |
| **Coordinators** | Compose functions, orchestrate workflows | `myapp.orders.service` |
| **Effectful Functions** | Handle I/O, marked with bang | `myapp.users.db` |
| **Adapters** | Transform data between systems | `myapp.external.stripe` |

### The Two Questions

For every namespace/function, ask:
1. **"What is its single purpose?"** - Can you describe it without "and"?
2. **"Is it pure?"** - Does it depend only on its arguments?

If you can't answer clearly, refactor.

---

## Tell, Don't Ask (Adapted for FP)

**Pass data in, get transformed data out. Don't query and decide externally.**

```clojure
;; BAD: Querying, then deciding outside
(if (>= (:balance account) amount)
  (assoc account :balance (- (:balance account) amount))
  account)

;; GOOD: Function encapsulates the decision
(defn withdraw [account amount]
  (if (>= (:balance account) amount)
    {:success true
     :account (update account :balance - amount)}
    {:success false
     :error :insufficient-funds
     :account account}))

;; Caller just handles the result
(let [{:keys [success account error]} (withdraw account 100)]
  (if success
    (save-account! account)
    (log-error error)))
```

The function that has context about the data should make the decision.

---

## Design by Contract

Functions have:
- **Preconditions** - What must be true of inputs (use `:pre` or schema)
- **Postconditions** - What will be true of outputs (use `:post` or schema)
- **Invariants** - What is always true about data shapes

```clojure
(require '[c3kit.apron.schema :as schema])

;; Define schemas for data shapes
(def balance-schema {:type :number :validate #(>= % 0)})
(def account-schema {:id {:type :string}
                     :balance balance-schema})
(def amount-schema {:type :number :validate pos?})

;; Function with schema validation
(defn withdraw [account amount]
  (schema/validate! account-schema account)
  (schema/validate! amount-schema amount)
  (if (>= (:balance account) amount)
    {:success true
     :account (update account :balance - amount)}
    {:success false
     :account account}))

;; Or use pre/post conditions
(defn withdraw [account amount]
  {:pre [(schema/valid? account-schema account)
         (schema/valid? amount-schema amount)]}
  (if (>= (:balance account) amount)
    {:success true
     :account (update account :balance - amount)}
    {:success false
     :account account}))
```

---

## Composition Over Inheritance

**Clojure naturally favors composition. There's no class inheritance.**

### Compose Data with Maps

```clojure
;; Instead of inheritance, compose data
(def base-user {:type :user :created-at (java.time.Instant/now)})

(defn make-premium-user [user]
  (assoc user
         :type :premium
         :discount-rate 0.2
         :features #{:priority-support :early-access}))

(defn make-admin-user [user]
  (assoc user
         :type :admin
         :permissions #{:read :write :delete :admin}))

;; Compose
(-> {:name "Alice" :email "alice@example.com"}
    (merge base-user)
    make-premium-user)
```

### Compose Behavior with Functions

```clojure
;; Instead of method overriding, compose functions
(defn with-logging [f]
  (fn [& args]
    (println "Calling with" args)
    (apply f args)))

(defn with-validation [validator f]
  (fn [& args]
    (when-not (apply validator args)
      (throw (ex-info "Validation failed" {:args args})))
    (apply f args)))

(def safe-withdraw
  (-> withdraw
      (with-validation #(and (map? %1) (pos? %2)))
      with-logging))
```

---

## Law of Demeter (Principle of Least Knowledge)

**Don't reach deep into nested data. Create accessor functions.**

```clojure
;; BAD: Reaching through data structures
(get-in order [:customer :address :city])

;; GOOD: Create accessor functions
(defn shipping-city [order]
  (get-in order [:customer :address :city]))

;; Even better: use namespaced keywords to flatten
(def order
  {:order/id "123"
   :order/shipping-city "Seattle"
   :order/customer-email "alice@example.com"})

(:order/shipping-city order)
```

This reduces coupling - changes to nested structure don't ripple through callers.

---

## Value Objects vs Entities

### Value Objects
- Defined by their attributes (no identity)
- Immutable (Clojure default)
- Comparable by value
- Examples: `Money`, `Email`, `Address`, `DateRange`

```clojure
;; Value objects as maps with schema
(require '[c3kit.apron.schema :as schema])

(def currency-schema {:type :keyword :validate #{:usd :eur :gbp}})
(def money-schema {:amount {:type :number}
                   :currency currency-schema})

(defn make-money [amount currency]
  {:pre [(number? amount) (#{:usd :eur :gbp} currency)]}
  {:amount amount :currency currency})

(defn add-money [m1 m2]
  {:pre [(= (:currency m1) (:currency m2))]}
  (update m1 :amount + (:amount m2)))

;; Or as records for type safety
(defrecord Money [amount currency])

(defn money [amount currency]
  (when-not (#{:usd :eur :gbp} currency)
    (throw (ex-info "Invalid currency" {:currency currency})))
  (->Money amount currency))
```

### Entities
- Have identity (survives attribute changes)
- Comparable by identity (`:id`)
- Examples: `User`, `Order`, `Product`

```clojure
;; Entities have an :id that defines identity
(defn make-user [name email]
  {:id (random-uuid)
   :name name
   :email email
   :created-at (java.time.Instant/now)})

;; Same entity after changes
(defn change-email [user new-email]
  (assoc user :email new-email)) ; Still same user (same :id)

;; Entity equality is by :id
(defn same-entity? [e1 e2]
  (= (:id e1) (:id e2)))
```

---

## Aggregates

A cluster of data treated as a single unit for changes.

```clojure
;; Order is the aggregate - it contains items
(defn make-order [customer-id]
  {:id (random-uuid)
   :customer-id customer-id
   :items []
   :status :draft})

;; All changes go through functions that maintain invariants
(defn add-item [order product quantity]
  (let [item {:product-id (:id product)
              :price (:price product)
              :quantity quantity}
        updated (update order :items conj item)]
    (validate-order-total! updated) ; Enforce invariant
    updated))

(defn remove-item [order product-id]
  (update order :items
    (fn [items]
      (remove #(= (:product-id %) product-id) items))))

;; Calculate derived values
(defn order-total [order]
  (->> (:items order)
       (map #(* (:price %) (:quantity %)))
       (reduce + 0)))

;; Don't let callers modify items directly
;; BAD: (update order :items conj {...}) - bypasses validation
;; GOOD: (add-item order product 2) - goes through aggregate
```

---

## Data Shapes for Different Purposes

### Input Data (from outside)
- Validate with schema
- Transform to internal representation

```clojure
(require '[c3kit.apron.schema :as schema])

(def email-schema {:type :string :validate #(re-matches #".+@.+\..+" %)})
(def create-user-request-schema
  {:name {:type :string}
   :email email-schema
   :age {:type :int :required false}})

(defn parse-request [raw]
  (schema/validate! create-user-request-schema raw)
  raw)
```

### Domain Data (internal)
- Rich structure, possibly records
- Business logic operates here

```clojure
(defrecord User [id name email created-at])

(defn create-user [request]
  (map->User
    {:id (random-uuid)
     :name (:name request)
     :email (:email request)
     :created-at (java.time.Instant/now)}))
```

### Output Data (to outside)
- Transform to external representation
- May hide internal details

```clojure
(defn user->response [user]
  (select-keys user [:id :name :email]))

(defn user->db-row [user]
  {:id (str (:id user))
   :name (:name user)
   :email (:email user)
   :created_at (.toString (:created-at user))})
```

---

## Polymorphism in Clojure

**Use multimethods or protocols when behavior varies by type.**

```clojure
;; Multimethod - dispatch on any value
(defmulti calculate-shipping :shipping-method)

(defmethod calculate-shipping :standard [{:keys [order-value]}]
  (if (< order-value 50) 5 0))

(defmethod calculate-shipping :express [_] 15)

(defmethod calculate-shipping :overnight [_] 25)

;; Protocol - dispatch on first argument's type
(defprotocol Discountable
  (apply-discount [this amount]))

(defrecord PercentDiscount [percent]
  Discountable
  (apply-discount [_ amount]
    (* amount (- 1 (/ percent 100)))))

(defrecord FixedDiscount [fixed]
  Discountable
  (apply-discount [_ amount]
    (max 0 (- amount fixed))))

;; Use plain functions when behavior doesn't vary
(defn calculate-tax [amount rate]
  (* amount rate))
```

---

## Summary: Data-Driven Design Principles

1. **Separate data from functions** - Data is plain, functions transform it
2. **Make data shapes explicit** - Use schema, records, or well-documented maps
3. **Keep functions pure** - Side effects at the edges only
4. **Compose, don't inherit** - Build up behavior by combining functions
5. **Use namespaces for boundaries** - Each namespace has one clear purpose
6. **Validate at boundaries** - Check data when it enters/leaves your system
