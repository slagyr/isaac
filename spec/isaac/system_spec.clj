(ns isaac.system-spec
  (:require
    [isaac.logger :as log]
    [isaac.system :as sut]
    [speclj.core :refer :all]))

(describe "isaac.system"

  (around [it]
    (sut/with-system {}
      (log/set-output! :memory)
      (log/clear-entries!)
      (it)))

  ;; region ----- register! / get round-trip -----

  (describe "register! and get"

    (it "stores and retrieves a value by key"
      (sut/register! :state-dir "/tmp/test")
      (should= "/tmp/test" (sut/get :state-dir)))

    (it "returns nil for an unregistered key"
      (should-be-nil (sut/get :state-dir)))

    (it "overwrites a previous value for the same key"
      (sut/register! :state-dir "/tmp/first")
      (sut/register! :state-dir "/tmp/second")
      (should= "/tmp/second" (sut/get :state-dir)))

    (it "stores values of any type"
      (let [a (atom {})]
        (sut/register! :tool-registry a)
        (should= a (sut/get :tool-registry)))))

  ;; endregion ^^^^^ register! / get round-trip ^^^^^

  ;; region ----- registered? -----

  (describe "registered?"

    (it "returns false before registration"
      (should-not (sut/registered? :state-dir)))

    (it "returns true after registration"
      (sut/register! :state-dir "/tmp/test")
      (should (sut/registered? :state-dir)))

    (it "returns false for an unrelated key"
      (sut/register! :state-dir "/tmp/test")
      (should-not (sut/registered? :server))))

  ;; endregion ^^^^^ registered? ^^^^^

  ;; region ----- with-system isolation -----

  (describe "with-system"

    (it "provides an empty system when initialized with {}"
      (sut/with-system {}
        (should-be-nil (sut/get :state-dir))))

    (it "provides an pre-populated system"
      (sut/with-system {:state-dir "/preset"}
        (should= "/preset" (sut/get :state-dir))))

    (it "isolates mutations from the outer scope"
      (sut/register! :state-dir "/outer")
      (sut/with-system {}
        (sut/register! :state-dir "/inner"))
      (should= "/outer" (sut/get :state-dir)))

    (it "inner scope does not see outer registrations"
      (sut/register! :state-dir "/outer")
      (sut/with-system {}
        (should-be-nil (sut/get :state-dir)))))

  ;; endregion ^^^^^ with-system isolation ^^^^^

  ;; region ----- schema / unknown-key warnings -----

  (describe "schema validation"

    (it "does not warn for a known schema key"
      (sut/register! :state-dir "/tmp/test")
      (should= [] (filter #(= :system/unknown-key (:event %)) (log/get-entries))))

    (it "does not warn for any of the known schema keys"
      (doseq [k [:server :session-store :config :tool-registry
                 :slash-registry :comm-registry :provider-registry
                 :active-turns :module-index]]
        (sut/register! k :anything))
      (should= [] (filter #(= :system/unknown-key (:event %)) (log/get-entries))))

    (it "logs a warning for an unknown unnamespaced key"
      (sut/register! :mystery-slot 42)
      (let [warnings (filter #(= :system/unknown-key (:event %)) (log/get-entries))]
        (should= 1 (count warnings))
        (should= :mystery-slot (:key (first warnings)))))

    (it "does not warn for a namespaced key (module extension point)"
      (sut/register! :my-module/state {:active true})
      (should= [] (filter #(= :system/unknown-key (:event %)) (log/get-entries)))))

  ;; endregion ^^^^^ schema / unknown-key warnings ^^^^^

  ;; region ----- schema structure -----

  (describe "schema"

    (it "is a map with :name :system"
      (should= :system (:name sut/schema)))

    (it "declares all expected keys"
      (let [ks (set (keys (:schema sut/schema)))]
        (should (contains? ks :state-dir))
        (should (contains? ks :server))
        (should (contains? ks :session-store))
        (should (contains? ks :config))
        (should (contains? ks :tool-registry))
        (should (contains? ks :slash-registry))
        (should (contains? ks :comm-registry))
        (should (contains? ks :provider-registry))
        (should (contains? ks :active-turns))
        (should (contains? ks :module-index)))))

  ;; endregion ^^^^^ schema structure ^^^^^

  )
