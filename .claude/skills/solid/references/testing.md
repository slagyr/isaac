# Testing Strategy for Clojure

## The Testing Pyramid

```
       /\
      /  \        E2E Tests (Few)
     /----\       - Full system
    /      \      - Slow, brittle
   /--------\
  /          \    Integration Tests (Some)
 /------------\   - Multiple components
/              \  - Medium speed
----------------
      Unit Tests (Many)
      - Single unit
      - Fast, isolated
```

## Test Types

### Unit Tests

Test ONE function in isolation.

**Characteristics:**
- Fast (milliseconds)
- No external dependencies
- Most of your tests should be unit tests

```clojure
(ns myapp.orders.pricing-spec
  (:require [speclj.core :refer :all]
            [myapp.orders.pricing :refer :all]))

(describe "calculate-total"
  (it "sums item prices"
    (let [items [{:price 100} {:price 50}]]
      (should= 150 (calculate-total items))))

  (it "returns 0 for empty items"
    (should= 0 (calculate-total []))))
```

### Integration Tests

Test multiple components together.

**Characteristics:**
- Slower (may use real DB)
- Test boundaries between components
- Fewer than unit tests

```clojure
(ns myapp.orders.service-spec
  (:require [speclj.core :refer :all]
            [myapp.orders.service :as service]
            [myapp.test-helpers :refer [with-test-db]]))

(describe "OrderService Integration"
  (with-all db (create-test-db))

  (it "saves and retrieves an order"
    (let [order {:customer-id "123" :items [{:sku "ABC" :price 100}]}
          saved (service/create-order! @db order)
          retrieved (service/get-order @db (:id saved))]
      (should= (:customer-id order) (:customer-id retrieved))
      (should= (:items order) (:items retrieved)))))
```

### E2E / Acceptance Tests

Test the entire system from user perspective.

**Characteristics:**
- Slowest
- Most brittle (many moving parts)
- Test critical paths only

```clojure
(describe "Checkout Flow"
  (it "user can complete purchase"
    (let [response (-> (session app)
                       (visit "/products")
                       (follow "Add to Cart")
                       (visit "/checkout")
                       (fill-in "Card" "4242424242424242")
                       (press "Pay"))]
      (should-contain "Order Confirmed" (:body response)))))
```

---

## Arrange-Act-Assert (AAA)

Structure EVERY test this way:

```clojure
(describe "premium discount"
  (it "applies 20% discount to premium users"
    ;; ARRANGE - Set up the test world
    (let [user {:premium? true}
          cart {:items [{:price 100}]}]
      ;; ACT - Execute the behavior under test
      (let [total (calculate-total cart user)]
        ;; ASSERT - Verify the expected outcome
        (should= 80 total)))))
```

### Writing AAA Backwards

Sometimes easier to write in reverse:

1. **Assert first** - What do you want to verify?
2. **Act** - What action produces that result?
3. **Arrange** - What setup is needed?

---

## Test Naming

### Bad: Abstract, Technical

```clojure
(it "should work correctly" ...)
(it "handles the edge case" ...)
(it "sets the data property" ...)
```

### Good: Concrete Examples, Domain Language

```clojure
(it "applies 20% discount for premium users" ...)
(it "returns error when cart is empty" ...)
(it "recognizes 'racecar' as a palindrome" ...)
```

### Format

```clojure
;; Option 1: behavior description
(it "applies tax based on shipping state" ...)

;; Option 2: when + then
(it "when adding 2 + 3, returns 5" ...)

;; Option 3: Given-When-Then with nested describe
(describe "given a premium user"
  (describe "when they checkout"
    (it "they receive 20% discount"
      ...)))
```

---

## Test Doubles

### Stub

Returns predefined values. In Clojure, often just a map or function.

```clojure
;; Stub as a map implementing a protocol
(def stub-repo
  (reify UserRepository
    (find-by-id [_ id] {:id id :name "Test User"})
    (save! [_ user] user)))

;; Or use with-redefs for function stubs
(it "uses stubbed data"
  (with-redefs [fetch-user (constantly {:name "Stubbed"})]
    (should= "Stubbed" (:name (fetch-user "123")))))
```

### Spy

Records how it was called.

```clojure
(defn make-spy []
  (let [calls (atom [])]
    {:fn (fn [& args] (swap! calls conj args))
     :calls calls}))

(it "tracks email sends"
  (let [spy (make-spy)
        send-email (:fn spy)]
    (send-email "user@example.com" "Hello")
    (should= [["user@example.com" "Hello"]] @(:calls spy))))
```

### Mock

Verifies expected interactions using `with-redefs`.

```clojure
(it "sends confirmation email on order"
  (let [email-sent (atom nil)]
    (with-redefs [email/send! (fn [to msg] (reset! email-sent {:to to :msg msg}))]
      (process-order! order)
      (should= "customer@example.com" (:to @email-sent)))))
```

### Fake

Working implementation (simplified).

```clojure
(defrecord InMemoryUserRepo [store]
  UserRepository
  (save! [_ user]
    (swap! store assoc (:id user) user)
    user)
  (find-by-id [_ id]
    (get @store id)))

(defn make-fake-repo []
  (->InMemoryUserRepo (atom {})))

;; Usage in tests
(describe "UserService"
  (with repo (make-fake-repo))

  (it "saves and retrieves user"
    (let [user {:id "123" :name "Alice"}]
      (save! @repo user)
      (should= user (find-by-id @repo "123")))))
```

---

## Testing Strategies by Layer

### Domain Layer (Most Tests)

- Unit tests with no mocks
- Test business rules, value objects, pure functions
- Fast, comprehensive

```clojure
(describe "Money"
  (it "adds amounts with same currency"
    (let [a {:amount 10 :currency :usd}
          b {:amount 20 :currency :usd}]
      (should= {:amount 30 :currency :usd}
               (add-money a b))))

  (it "throws when adding different currencies"
    (let [usd {:amount 10 :currency :usd}
          eur {:amount 10 :currency :eur}]
      (should-throw clojure.lang.ExceptionInfo
        (add-money usd eur)))))
```

### Application Layer

- Integration tests with fake infrastructure
- Test use case orchestration

```clojure
(describe "CreateOrderUseCase"
  (with order-repo (make-fake-repo))
  (with email-sent (atom nil))

  (around [it]
    (with-redefs [email/send! #(reset! @email-sent {:to %1 :msg %2})]
      (it)))

  (it "creates order and sends confirmation"
    (let [result (create-order! {:repo @order-repo}
                                {:customer-id "123"
                                 :email "test@example.com"
                                 :items [{:sku "ABC" :price 100}]})]
      (should-not-be-nil (:id result))
      (should= "test@example.com" (:to @@email-sent)))))
```

### Infrastructure Layer

- Integration tests with real dependencies
- Test database, API integrations

```clojure
(describe "PostgresOrderRepo"
  (with-all db (create-test-db))

  (before-all (migrate! @db))
  (after (clear-orders! @db))

  (it "persists and retrieves order"
    (let [repo (->PostgresOrderRepo @db)
          order {:id (random-uuid)
                 :customer-id "123"
                 :items [{:sku "ABC" :price 100}]}]
      (save! repo order)
      (should= order (find-by-id repo (:id order))))))
```

---

## High-Value Integration Tests

Focus integration tests on:

1. **Boundaries** - Where systems meet
2. **Critical paths** - Money, security, core features
3. **Complex queries** - Database operations

### Contract Tests

Verify implementations match protocol contracts.

```clojure
;; Shared contract test
(defn user-repo-contract [make-repo]
  (describe "UserRepo Contract"
    (with repo (make-repo))

    (it "saves and retrieves user"
      (let [user {:id "123" :name "Test"}]
        (save! @repo user)
        (should= user (find-by-id @repo "123"))))

    (it "returns nil for missing user"
      (should-be-nil (find-by-id @repo "nonexistent")))))

;; Apply to all implementations
(describe "InMemoryUserRepo"
  (user-repo-contract #(->InMemoryUserRepo (atom {}))))

(describe "PostgresUserRepo"
  (user-repo-contract #(->PostgresUserRepo test-db)))
```

---

## Test Builders

Create test objects easily.

```clojure
(defn order-builder
  ([] (order-builder {}))
  ([overrides]
   (merge {:id (random-uuid)
           :customer-id "cust-1"
           :items []
           :status :pending
           :created-at (java.time.Instant/now)}
          overrides)))

;; Usage
(order-builder)
(order-builder {:status :paid :items [{:sku "ABC" :price 100}]})

;; Composable builders
(defn with-items [order items]
  (assoc order :items items))

(defn paid [order]
  (assoc order :status :paid))

(-> (order-builder)
    (with-items [{:sku "ABC" :price 100}])
    paid)
```

---

## Speclj Features

### Context Management

```clojure
(describe "OrderService"
  ;; Setup once for all tests
  (with-all db (create-test-db))

  ;; Fresh for each test
  (with order (order-builder))

  ;; Run before each test
  (before (reset-db! @db))

  ;; Run after each test
  (after (clear-cache!))

  ;; Wrap each test
  (around [it]
    (binding [*current-user* test-user]
      (it)))

  (it "processes order"
    (should-not-throw (process! @order))))
```

### Assertions

```clojure
;; Equality
(should= expected actual)
(should-not= unexpected actual)

;; Truthiness
(should truthy-value)
(should-not falsy-value)
(should-be-nil value)
(should-not-be-nil value)

;; Exceptions
(should-throw ExceptionType (code-that-throws))
(should-throw ExceptionType "message" (code-that-throws))
(should-not-throw (code-that-succeeds))

;; Collections
(should-contain item collection)
(should-not-contain item collection)

;; Approximate equality (for floats)
(should== 3.14159 pi 0.001)
```

---

## Common Testing Mistakes

| Mistake | Problem | Solution |
|---------|---------|----------|
| Testing implementation | Brittle tests | Test behavior only |
| Too many mocks | Tests prove nothing | Use real objects when possible |
| Shared mutable state | Flaky tests | Use `with` for fresh state |
| No assertions | False confidence | Always assert something meaningful |
| Testing trivial code | Wasted effort | Focus on logic and edge cases |
| Slow tests | Reduced feedback | More unit tests, fewer integration |
| Testing private functions | Coupled to implementation | Test through public API |
