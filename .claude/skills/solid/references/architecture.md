# Software Architecture for Clojure

## The Goal of Architecture

Enable the development team to:
1. **Add** features with minimal friction
2. **Change** existing features safely
3. **Remove** features cleanly
4. **Test** features in isolation
5. **Deploy** independently when possible

## Architectural Principles

### 1. Namespace Organization (Feature-First, Screaming Architecture)

Organize by **feature**, not by technical layer.

```
BAD: Layer-first
src/
  myapp/
    controllers/
      user_handler.clj
      order_handler.clj
    services/
      user_service.clj
      order_service.clj
    repositories/
      user_repo.clj
      order_repo.clj

GOOD: Feature-first
src/
  myapp/
    users/
      core.clj          ; Domain logic (pure functions)
      db.clj            ; Persistence (effectful)
      handlers.clj      ; HTTP handlers
      schema.clj        ; Data schema
    orders/
      core.clj
      db.clj
      handlers.clj
      schema.clj
    shared/
      db.clj            ; Shared DB utilities
      middleware.clj    ; Shared Ring middleware
```

**Why:** Changes to "users" feature stay in `users/`. High cohesion within features.

### 2. Horizontal Boundaries (Layers)

Separate concerns into layers with clear dependencies.

```
┌──────────────────────────────────────┐
│           Presentation               │  HTTP handlers, CLI
├──────────────────────────────────────┤
│           Application                │  Use Cases, Orchestration
├──────────────────────────────────────┤
│             Domain                   │  Business Logic (pure functions)
├──────────────────────────────────────┤
│          Infrastructure              │  Database, APIs, External
└──────────────────────────────────────┘
```

### 3. The Dependency Rule

**Dependencies point INWARD toward pure domain logic.**

```
Infrastructure → Application → Domain
      ↓               ↓            ↓
   (outer)        (middle)      (inner)
```

- Inner layers know NOTHING about outer layers
- Domain has zero dependencies on infrastructure
- Use protocols to invert dependencies

```clojure
;; Domain defines the schema (inner)
(ns myapp.users.core)

(defn create-user
  "Pure domain logic - no infrastructure dependency"
  [name email]
  {:id (random-uuid)
   :name name
   :email email
   :created-at (java.time.Instant/now)})

;; Application uses the schema (middle)
(ns myapp.users.service
  (:require [myapp.users.core :as users]
            [c3kit.bucket.api :as db]))

(defn register-user!
  "Orchestrates user creation - depends on abstraction"
  [repo email-service name email]
  (let [user (users/create-user name email)
        user (db/tx user)]
    (send-welcome-email! email-service user)
    user))
```

### 4. Protocols at Boundaries

Protocols define contracts between components.

```clojure
;; The contract
(defprotocol PaymentGateway
  (charge [this amount card-details])
  (refund [this charge-id]))

;; Multiple implementations possible
(defrecord StripeGateway [api-key]
  PaymentGateway
  (charge [_ amount card] ...)
  (refund [_ charge-id] ...))

(defrecord PayPalGateway [client-id secret]
  PaymentGateway
  (charge [_ amount card] ...)
  (refund [_ charge-id] ...))

;; For tests
(defrecord MemoryGateway [charges-atom]
  PaymentGateway
  (charge [_ amount card]
    (let [id (random-uuid)]
      (swap! charges-atom assoc id {:amount amount :card card})
      {:charge-id id :success true}))
  (refund [_ charge-id]
    (swap! charges-atom dissoc charge-id)
    {:success true}))
```

### 5. Dependency Management

Use a single application config file to store settings, typically as string and keywords, for various environments.  Features can dispatch off config values, typically using multimethods, to achieve decoupled implementations.

```clojure
(ns app.config)

(def base
  {
   :analytics      {:target :log}
   :log-level      :trace
   :site-root      :define-me                            
   :db             {:impl :memory}
   })

(def development (merge base {:site-root "http://localhost:8080"}))

(def staging
  (merge base {:site-root "http://staging.mysite.com:8080"
               :db {:impl :jdbc :dialect :h2}}))

(def production
  (merge base {:site-root "http://mysite.com:8080"
               :db {:impl :jdbc :dialect :h2}}))

(def environment (app/find-env "c3.env" "C3_ENV"))
(defn development? [] (= "development" environment))
(defn staging? [] (= "staging" environment))
(defn production? [] (= "production" environment))

(def env
  (case environment
        "staging" staging
        "production" production
        development))
```

### 6. Cross-Cutting Concerns

Concerns that span multiple features: logging, auth, validation, error handling.

**Use middleware in Ring:**

```clojure
(defn wrap-logging [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          elapsed (- (System/currentTimeMillis) start)]
      (log/info "Request" (:uri request) "took" elapsed "ms")
      response)))

(defn wrap-auth [handler]
  (fn [request]
    (if-let [user (authenticate request)]
      (handler (assoc request :user user))
      {:status 401 :body "Unauthorized"})))

(defn wrap-error-handling [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        {:status 400 :body (ex-message e)})
      (catch Exception e
        (log/error e "Unexpected error")
        {:status 500 :body "Internal error"}))))

(def app
  (-> routes
      wrap-auth
      wrap-logging
      wrap-error-handling))
```

---

## Common Architectural Styles

### Layered Architecture

Traditional layers: Presentation → Application → Domain → Infrastructure

**In Clojure:**
```
handlers.clj    → service.clj    → core.clj    → db.clj
(HTTP/CLI)        (orchestration)   (pure logic)  (persistence)
```

### Hexagonal Architecture (Ports & Adapters)

Domain at center, adapters around the edges.

```
        ┌─────────────────────┐
        │    HTTP Adapter     │  (handlers.clj)
        └─────────┬───────────┘
                  │
┌─────────────────▼─────────────────┐
│              DOMAIN                │
│   ┌─────────────────────────┐     │
│   │    Business Logic        │     │  (core.clj - pure functions)
│   │    Protocols             │     │
│   └─────────────────────────┘     │
└─────────────────┬─────────────────┘
                  │
        ┌─────────▼──────────────────┐
        │   Implementation Adapter   │  (impl.clj)
        └────────────────────────────┘
```

**Ports:** Protocols defined by the domain
**Adapters:** Implementations that connect to the outside world

---

## Feature-Driven Structure (Screaming Architecture)

### Backend (Ring/Pedestal)

```
src/
  myapp/
    core.clj              ; Entry point, system setup
    users/
      core.clj            ; Domain: User record, validation, pure functions
      schema.clj          ; Data schema for user data
      db.clj              ; UserRepository protocol + Postgres impl
      handlers.clj        ; Ring handlers
      routes.clj          ; Route definitions
    orders/
      core.clj
      schema.clj
      db.clj
      handlers.clj
      routes.clj
    shared/
      db.clj              ; DB connection, migrations
      middleware.clj      ; Shared Ring middleware
      http.clj            ; HTTP helpers
```

### Web Frontend (ClojureScript)

```
src/
  myapp/
    core.cljs             ; Entry point
    users/
      views.cljs          ; User UI components
      events.cljs         ; Re-frame events
      subs.cljs           ; Re-frame subscriptions
      db.cljs             ; User-related app-db structure
    orders/
      views.cljs
      events.cljs
      subs.cljs
      db.cljs
    shared/
      components.cljs     ; Shared UI components
      http.cljs           ; API client
```

---

## The Walking Skeleton

Start with a minimal end-to-end slice:

1. **Thinnest possible feature** that touches all layers
2. **Deployable** from day one
3. **Proves the architecture** works

Example walking skeleton for e-commerce:
- User can view ONE product (hardcoded)
- User can add it to cart
- User can "checkout" (just logs)

```clojure
;; Start simple, prove the wiring works
(defn get-product [id]
  {:id "prod-1" :name "Widget" :price 100})

(defn add-to-cart [cart product]
  (update cart :items conj product))

(defn checkout! [cart]
  (log/info "Checkout:" cart)
  {:success true})
```

From there, flesh out each feature fully.

---

## Testing Architecture

```
┌────────────────────────────────────────────┐
│            E2E / Acceptance Tests          │  Few, slow, high confidence
├────────────────────────────────────────────┤
│            Integration Tests               │  Some, medium speed
├────────────────────────────────────────────┤
│              Unit Tests                    │  Many, fast, isolated
└────────────────────────────────────────────┘
```

**Test by layer:**
- **Domain (core.clj):** Unit tests (most tests here, pure functions)
- **Application (service.clj):** Integration tests with fake repos
- **Infrastructure (db.clj):** Integration tests with real DB
- **E2E:** Critical paths only

---

## Architecture Decision Records (ADRs)

Document significant decisions:

```markdown
# ADR 001: Use PostgreSQL for persistence

## Status
Accepted

## Context
We need a database. Options: Datomic, PostgreSQL, MongoDB

## Decision
Datomic or PostgreSQL for:
- ACID compliance
- Team familiarity
- Good Clojure library support (c3kit.bucket)
- JSON support for flexibility

## Consequences
- Need PostgreSQL expertise
- Schema migrations required (ragtime/migratus)
- Excellent query capabilities
```

---

## Red Flags in Architecture

- **Circular dependencies** between namespaces
- **Domain depending on infrastructure** (core.clj requiring db.clj)
- **Framework code in business logic**
- **No clear boundaries** between features
- **Shared mutable state** across namespaces
- **"Util" or "common" namespaces** that grow forever
- **Database schema driving domain model**
- **Handlers containing business logic** (should be in core.clj)
