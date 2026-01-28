# SOLID Principles for Clojure

## Overview

SOLID helps structure software to be flexible, maintainable, and testable. In Clojure, these principles translate naturally to functional programming idioms.

## SOLID → Clojure Translation

| OOP Principle | Clojure Translation |
|---------------|---------------------|
| **SRP** | Functions do one thing; namespaces have cohesive purpose |
| **OCP** | Protocols & multimethods for extension without modification |
| **LSP** | Protocol implementations honor contracts |
| **ISP** | Small focused protocols (natural in Clojure) |
| **DIP** | Depend on protocols, inject implementations via arguments |

---

## S - Single Responsibility Principle (SRP)

> "A function should do one thing. A namespace should have one cohesive purpose."

### Problem It Solves
God namespaces that do everything - hard to test, hard to change, hard to understand.

### How to Apply
Each function handles ONE task. Each namespace has ONE cohesive purpose. If you find yourself saying "and" when describing what a function does, split it.

```clojure
;; BAD: Multiple responsibilities in one function
(defn process-order [order]
  ;; Calculates total
  (let [total (reduce + (map :price (:items order)))]
    ;; Saves to database
    (db/tx (assoc order :total total))
    ;; Sends notification
    (send-email (:customer-email order) "Order confirmed")))

;; GOOD: Single responsibility each
(ns myapp.orders.pricing)
(defn calculate-total [order]
  (reduce + (map :price (:items order))))

(ns myapp.orders.notifications)
(defn send-confirmation! [email-service order]
  (send-email email-service (:customer-email order) "Order confirmed"))

;; Coordinator namespace composes them
(ns myapp.orders.service)
(defn process-order! [deps order]
  (let [order-with-total (assoc order :total (pricing/calculate-total order))]
    (db/tx order-with-total)
    (notifications/send-confirmation! (:email deps) order-with-total)))
```

### Detection Questions
- Does this function/namespace have multiple reasons to change?
- Can I describe it without using "and"?
- Would different stakeholders request changes to different parts?

---

## O - Open/Closed Principle (OCP)

> "Software entities should be open for extension but closed for modification."

### Problem It Solves
Having to modify existing, tested code every time requirements change. Risk of breaking working features.

### How to Apply
Use protocols and multimethods to allow new behavior through new implementations, not edits to existing code.

```clojure
;; BAD: Must modify to add new shipping
(defn calculate-shipping [type order-value]
  (cond
    (= type :standard) (if (< order-value 50) 5 0)
    (= type :express) 15
    ;; Must add more conditions for new types!
    ))

;; GOOD: Use multimethods
(defmulti calculate-shipping :method)

(defmethod calculate-shipping :standard [{:keys [order-value]}]
  (if (< order-value 50) 5 0))

(defmethod calculate-shipping :express [_] 15)

;; Extend by adding new defmethod, no changes to existing code
(defmethod calculate-shipping :same-day [_] 25)

;; ALTERNATIVE: Open for extension via protocols
(defprotocol ShippingMethod
             (calculate-cost [this order-value]))

(defrecord StandardShipping []
           ShippingMethod
           (calculate-cost [_ order-value]
                           (if (< order-value 50) 5 0)))

(defrecord ExpressShipping []
           ShippingMethod
           (calculate-cost [_ _] 15))

;; Add new shipping by creating new record, not modifying existing
(defrecord SameDayShipping []
           ShippingMethod
           (calculate-cost [_ _] 25))
```

### Architectural Insight
OCP at architecture level means: **design your codebase so new features are added by adding code, not changing existing code.**

---

## L - Liskov Substitution Principle (LSP)

> "Protocol implementations must be substitutable without altering program correctness."

### Problem It Solves
Implementations that break expectations, requiring type-checking and special cases.

### How to Apply
All protocol implementations must honor the contract. If the protocol method should return positive numbers, all implementations must return positive numbers.

```clojure
;; BAD: Violates contract
(defprotocol DiscountPolicy
  (discount [this value]))

(defrecord NoDiscount []
  DiscountPolicy
  (discount [_ _] 0)) ; OK - non-negative

(defrecord WeirdDiscount []
  DiscountPolicy
  (discount [_ _] -5)) ; BAD - increases cost! Breaks expectations

;; GOOD: All implementations honor the contract
(defrecord PercentDiscount [percent]
  DiscountPolicy
  (discount [_ value]
    {:pre [(>= percent 0) (<= percent 100)]}
    (* value (/ percent 100))))

(defrecord FixedDiscount [amount]
  DiscountPolicy
  (discount [_ value]
    {:pre [(>= amount 0)]}
    (min amount value))) ; Never more than the value
```

### Key Insight
This is why you can swap `InMemoryUserRepo` for `PostgresUserRepo` - they both honor the `UserRepository` protocol contract.

```clojure
;; Both implementations are substitutable
(defprotocol UserRepository
  (save! [this user])
  (find-by-id [this id]))

(defrecord InMemoryUserRepo [store]
  UserRepository
  (save! [_ user] (swap! store assoc (:id user) user))
  (find-by-id [_ id] (get @store id)))

(defrecord PostgresUserRepo [db]
  UserRepository
  (save! [_ user] (jdbc/insert! db :users user))
  (find-by-id [_ id] (first (jdbc/query db ["SELECT * FROM users WHERE id = ?" id]))))
```

---

## I - Interface Segregation Principle (ISP)

> "Clients should not be forced to depend on methods they do not use."

### Problem It Solves
Fat protocols that force partial implementations or unused methods.

### How to Apply
Split large protocols into smaller, cohesive ones. This is natural in Clojure - protocols tend to be small.

```clojure
;; BAD: Fat protocol
(defprotocol WarehouseDevice
  (print-label [this order-id])
  (scan-barcode [this])
  (package-item [this order-id]))

;; BasicPrinter can't implement scan or package!

;; GOOD: Segregated protocols
(defprotocol LabelPrinter
  (print-label [this order-id]))

(defprotocol BarcodeScanner
  (scan-barcode [this]))

(defprotocol ItemPackager
  (package-item [this order-id]))

;; Each device implements only what it can do
(defrecord BasicPrinter []
  LabelPrinter
  (print-label [_ order-id]
    (println "Printing label for" order-id)))

(defrecord FullWarehouseStation []
  LabelPrinter
  (print-label [_ order-id] ...)
  BarcodeScanner
  (scan-barcode [_] ...)
  ItemPackager
  (package-item [_ order-id] ...))
```

### Detection
If you find yourself throwing exceptions for "not implemented" methods, the protocol is too fat.

---

## D - Dependency Inversion Principle (DIP)

> "High-level modules should not depend on low-level modules. Both should depend on abstractions."

### Problem It Solves
Tight coupling to specific implementations (databases, APIs, frameworks). Hard to test, hard to swap.

### How to Apply
Depend on protocols, inject implementations as function arguments or in a deps map.

```clojure
;; BAD: Direct dependency on concrete implementation
(defn confirm-order [order]
  ;; Locked to SendGrid!
  (sendgrid/send-email (:email order) "Order confirmed"))

;; GOOD: Depend on abstraction (protocol or multimethod)
(defprotocol EmailService
  (send! [this to message]))

(defrecord SendGridEmail [api-key]
  EmailService
  (send! [_ to message]
    (sendgrid/send api-key to message)))

(defrecord SESEmail [client]
  EmailService
  (send! [_ to message]
    (aws/send-email client to message)))

;; For tests
(defrecord MockEmail [sent-atom]
  EmailService
  (send! [_ to message]
    (swap! sent-atom conj {:to to :message message})))

;; Business logic depends on abstraction
(defn confirm-order [email-service order]
  (send! email-service (:email order) "Order confirmed"))

;; Inject at composition root
(def prod-deps {:email (->SendGridEmail "api-key")})
(def test-deps {:email (->MockEmail (atom []))})
```

### The Dependency Rule
Source code dependencies should point **inward** toward high-level policies (domain logic), never toward low-level details (infrastructure).

```
Infrastructure → Application → Domain
      ↑              ↑            ↑
    (outer)       (middle)     (inner)

Dependencies flow: outer → inner
Never: inner → outer
```

---

## Applying SOLID at Architecture Level

These principles scale beyond functions:

| Principle | Architecture Application |
|-----------|--------------------------|
| SRP | Each namespace/bounded context has one responsibility |
| OCP | New features = new namespaces/functions, not edits to existing |
| LSP | Services with same protocol are substitutable |
| ISP | Thin protocols between services |
| DIP | Domain logic doesn't know about databases/frameworks |

---

## Quick Reference

| Principle | One-Liner | Red Flag |
|-----------|-----------|----------|
| SRP | One reason to change | "This namespace handles X and Y and Z" |
| OCP | Add, don't modify | `cond` chains for types |
| LSP | Implementations are substitutable | Type-checking in calling code |
| ISP | Small, focused protocols | Throwing "not implemented" |
| DIP | Depend on abstractions | Hardcoded infrastructure calls |
