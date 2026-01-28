# Code Smells in Clojure

## What Are Code Smells?

Indicators that something MAY be wrong. Not bugs, but design problems that make code hard to understand, change, or test.

## Categories of Smells

### 1. Bloaters
Code that has grown too large.

| Smell | Symptom | Refactoring |
|-------|---------|-------------|
| **Long Function** | > 10 lines | Extract functions, use threading |
| **Large Namespace** | > 100 lines, multiple responsibilities | Extract namespaces |
| **Long Parameter List** | > 3 parameters | Use a map |
| **Data Clumps** | Same group of values appear together | Extract into a map/record |
| **Primitive Obsession** | Primitives instead of domain types | Wrap in schema or records |

### 2. Coupling Smells
Excessive coupling between namespaces/functions.

| Smell | Symptom | Refactoring |
|-------|---------|-------------|
| **Feature Envy** | Function uses another namespace's data extensively | Move function |
| **Inappropriate Intimacy** | Functions know too much about other namespaces | Better abstractions |
| **Deep Nesting** | `get-in` with long paths | Accessor functions |
| **Global State** | Relying on atoms/vars defined elsewhere | Pass as arguments |

### 3. Change Preventers
Code that makes changes difficult.

| Smell | Symptom | Refactoring |
|-------|---------|-------------|
| **Divergent Change** | One namespace changed for many reasons | Extract namespaces (SRP) |
| **Shotgun Surgery** | One change touches many namespaces | Move related code together |

### 4. Dispensables
Code that can be removed.

| Smell | Symptom | Refactoring |
|-------|---------|-------------|
| **Comments** | Explaining bad code | Rename, extract function |
| **Duplicate Code** | Copy-paste | Extract function |
| **Dead Code** | Unreachable code | Delete |
| **Speculative Generality** | "Just in case" code | Delete (YAGNI) |

---

## The Most Common Clojure Smells

### 1. Long Function

**Symptom:** Function > 10 lines, doing multiple things.

```clojure
;; SMELL
(defn process-order [order]
  ;; Validate
  (when-not (:items order)
    (throw (ex-info "Empty order" {})))
  (when-not (:customer order)
    (throw (ex-info "No customer" {})))

  ;; Calculate
  (let [subtotal (reduce + (map :price (:items order)))
        tax-rate (get-tax-rate (get-in order [:customer :state]))
        tax (* subtotal tax-rate)
        total (+ subtotal tax)]

    ;; Save
    (db/tx :orders (assoc order :total total))

    ;; Notify
    (email/send! (get-in order [:customer :email]) "Order confirmed")

    {:order-id (:id order) :total total}))

;; REFACTORED
(defn- validate-order! [order]
  (when-not (:items order)
    (throw (ex-info "Empty order" {})))
  (when-not (:customer order)
    (throw (ex-info "No customer" {})))
  order)

(defn- calculate-totals [order]
  (let [subtotal (->> order :items (map :price) (reduce +))
        tax (* subtotal (get-tax-rate (get-in order [:customer :state])))]
    (assoc order :subtotal subtotal :tax tax :total (+ subtotal tax))))

(defn process-order! [deps order]
  (let [order (-> order validate-order! calculate-totals)]
    ((:save! deps) order)
    ((:notify! deps) order)
    (select-keys order [:id :total])))
```

### 2. Large Namespace

**Symptom:** Namespace with many responsibilities, > 100 lines.

```clojure
;; SMELL: God namespace
(ns myapp.user)

;; User data
(defrecord User [id name email])

;; Authentication
(defn login [credentials] ...)
(defn logout [user] ...)
(defn reset-password [email] ...)

;; Preferences
(defn set-theme [user theme] ...)
(defn set-language [user lang] ...)

;; Notifications
(defn send-email [user message] ...)
(defn send-sms [user message] ...)

;; Billing
(defn charge [user amount] ...)
(defn refund [user payment] ...)

;; REFACTORED: Separate namespaces
(ns myapp.users.core)       ; User data structures
(ns myapp.users.auth)       ; Authentication
(ns myapp.users.prefs)      ; Preferences
(ns myapp.notifications)    ; Notification sending
(ns myapp.billing)          ; Payment processing
```

### 3. Deep Nesting / Train Wrecks

**Symptom:** Long chains of `get-in` or nested function calls.

```clojure
;; SMELL: Reaching deep into data
(defn calculate-shipping [order]
  (let [country (get-in order [:customer :address :country])
        state (get-in order [:customer :address :state])
        city (get-in order [:customer :address :city])]
    (cond
      (= country "US") (if (= state "CA") 10 15)
      :else 25)))

;; REFACTORED: Accessor functions
(defn customer-country [order]
  (get-in order [:customer :address :country]))

(defn customer-state [order]
  (get-in order [:customer :address :state]))

(defn calculate-shipping [order]
  (cond
    (= (customer-country order) "US")
    (if (= (customer-state order) "CA") 10 15)
    :else 25))

;; OR: Use namespaced keywords to flatten
(def order
  {:order/id "123"
   :order/customer-country "US"
   :order/customer-state "CA"})

(defn calculate-shipping [{:order/keys [customer-country customer-state]}]
  (cond
    (= customer-country "US") (if (= customer-state "CA") 10 15)
    :else 25))
```

### 4. Feature Envy

**Symptom:** Function uses another namespace's data more than its own.

```clojure
;; SMELL: order functions "envy" customer data
(ns myapp.orders)

(defn calculate-shipping [order]
  (let [customer (:customer order)]
    (if (= (:country customer) "US")
      (cond
        (= (:state customer) "CA") 10
        (= (:state customer) "NY") 12
        :else 15)
      25)))

;; REFACTORED: Move to customer namespace
(ns myapp.customers)

(defn shipping-cost [customer]
  (if (= (:country customer) "US")
    (cond
      (= (:state customer) "CA") 10
      (= (:state customer) "NY") 12
      :else 15)
    25))

(ns myapp.orders)

(defn calculate-shipping [order]
  (customers/shipping-cost (:customer order)))
```

### 5. Primitive Obsession

**Symptom:** Using primitives for domain concepts.

```clojure
;; SMELL
(defn create-user [email age zip-code]
  ;; No validation, easy to pass wrong values
  {:email email :age age :zip-code zip-code})

;; Easy to make mistakes
(create-user 25 "user@example.com" "12345") ; Wrong order!

;; REFACTORED: Domain types with validation
(require '[c3kit.apron.schema :as schema])

(def email-schema {:type :string :validate #(re-matches #".+@.+\..+" %)})
(def age-schema {:type :int :validate #(<= 0 % 150)})
(def zip-code-schema {:type :string :validate #(re-matches #"\d{5}" %)})

(def user-input-schema
  {:email email-schema
   :age age-schema
   :zip-code zip-code-schema})

(defn create-user [input]
  (schema/validate! user-input-schema input)
  (assoc input :id (random-uuid)))

;; Usage is clearer
(create-user {:email "user@example.com" :age 25 :zip-code "12345"})
```

### 6. Nested Conditionals

**Symptom:** Deep if/cond nesting, hard to follow.

```clojure
;; SMELL: Deep nesting
(defn get-shipping-method [order]
  (if (:express? order)
    (if (< (:total order) 100)
      :express-standard
      :express-free)
    (if (< (:total order) 50)
      :standard-paid
      (if (:premium-customer? order)
        :standard-free
        :standard-discounted))))

;; REFACTORED: Use cond or multimethod
(defn get-shipping-method [order]
  (let [{:keys [express? total premium-customer?]} order]
    (cond
      (and express? (< total 100))      :express-standard
      express?                           :express-free
      (< total 50)                       :standard-paid
      premium-customer?                  :standard-free
      :else                              :standard-discounted)))

;; OR: Use a map for dispatch
(def shipping-rules
  [{:match #(and (:express? %) (< (:total %) 100)) :method :express-standard}
   {:match :express?                                :method :express-free}
   {:match #(< (:total %) 50)                       :method :standard-paid}
   {:match :premium-customer?                       :method :standard-free}
   {:match (constantly true)                        :method :standard-discounted}])

(defn get-shipping-method [order]
  (->> shipping-rules
       (filter #((:match %) order))
       first
       :method))
```

### 7. Speculative Generality

**Symptom:** "Just in case" abstractions that aren't used.

```clojure
;; SMELL: Over-engineered for hypothetical needs
(defprotocol PaymentProcessor
  (process [this payment])
  (rollback [this payment-id])
  (audit [this])
  (generate-report [this options])
  (schedule-recurring [this payment schedule]))

(defrecord StripeProcessor []
  PaymentProcessor
  (process [_ payment] ...)           ; Used
  (rollback [_ _] (throw (ex-info "Not implemented" {})))
  (audit [_] (throw (ex-info "Not implemented" {})))
  (generate-report [_ _] (throw (ex-info "Not implemented" {})))
  (schedule-recurring [_ _ _] (throw (ex-info "Not implemented" {}))))

;; REFACTORED: YAGNI
(defprotocol PaymentProcessor
  (process [this payment]))

(defrecord StripeProcessor []
  PaymentProcessor
  (process [_ payment] ...))

;; Add methods when actually needed
```

### 8. Side Effects Hidden in Pure-Looking Functions

**Symptom:** Function name doesn't indicate side effects.

```clojure
;; SMELL: Looks pure but has side effects
(defn calculate-total [order]
  (let [total (->> order :items (map :price) (reduce +))]
    (log/info "Calculated total" total)      ; Side effect!
    (metrics/record! :order-total total)     ; Side effect!
    total))

;; REFACTORED: Separate pure and impure
(defn calculate-total [order]
  (->> order :items (map :price) (reduce +)))

(defn calculate-and-record-total! [order]
  (let [total (calculate-total order)]
    (log/info "Calculated total" total)
    (metrics/record! :order-total total)
    total))
```

---

## Prevention Strategies

1. **Keep functions small** - Under 10 lines
2. **One purpose per namespace** - SRP
3. **Practice TDD** - Tests reveal design problems early
4. **Use schema/records** - For domain types
5. **Mark side effects with bang suffix** - Clear naming
6. **Threading macros** - Flatten nesting
7. **Refactor continuously** - Don't let smells accumulate

---

## When You Find a Smell

1. **Confirm it's a problem** - Not all smells need fixing
2. **Ensure test coverage** - Before refactoring
3. **Refactor in small steps** - Keep tests passing
4. **Commit frequently** - Easy to revert if needed
