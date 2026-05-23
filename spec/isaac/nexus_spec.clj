(ns isaac.nexus-spec
  (:require
    [isaac.logger :as log]
    [isaac.nexus :as sut]
    [speclj.core :refer :all]))

(defn- with-fresh-system [f]
  (sut/-with-nexus {}
    (log/set-output! :memory)
    (log/clear-entries!)
    (f)))

(describe "isaac.nexus"

  (around [it]
    (with-fresh-system it))

  ;; region ----- register! / get round-trip -----

  (describe "install! and current"

    (it "installs and returns the current runtime"
      (let [runtime {:state-dir "/tmp/test"
                     :config    (atom {:crew {}})}]
        (sut/install! runtime)
        (should= runtime (sut/necho))
        (should= "/tmp/test" (sut/get :state-dir)))))

  (describe "register! and get"

    (it "stores and retrieves a flat value by path"
      (sut/register! [:state-dir] "/tmp/test")
      (should= "/tmp/test" (sut/get :state-dir)))

    (it "returns nil for an unregistered key"
      (should-be-nil (sut/get :state-dir)))

    (it "overwrites a previous value for the same path"
      (sut/register! [:state-dir] "/tmp/first")
      (sut/register! [:state-dir] "/tmp/second")
      (should= "/tmp/second" (sut/get :state-dir)))

    (it "stores values of any type"
      (let [a (atom {})]
        (sut/register! [:tool-registry] a)
        (should= a (sut/get :tool-registry)))))

  ;; endregion ^^^^^ register! / get round-trip ^^^^^

  ;; region ----- get-in -----

  (describe "get-in"

    (it "returns a flat value by single-element path"
      (sut/register! [:state-dir] "/tmp/test")
      (should= "/tmp/test" (sut/get-in [:state-dir])))

    (it "returns a nested value by multi-element path"
      (sut/register! [:sessions :store] ::store)
      (should= ::store (sut/get-in [:sessions :store])))

    (it "returns nil for a missing path"
      (should-be-nil (sut/get-in [:sessions :store])))

    (it "does not disturb sibling keys when registering at a path"
      (sut/register! [:sessions :store] ::store)
      (sut/register! [:sessions :naming-strategy] ::strategy)
      (should= ::store (sut/get-in [:sessions :store]))
      (should= ::strategy (sut/get-in [:sessions :naming-strategy]))))

  ;; endregion ^^^^^ get-in ^^^^^

  ;; region ----- registered? -----

  (describe "registered?"

    (it "returns false before registration"
      (should-not (sut/registered? [:state-dir])))

    (it "returns true after registration"
      (sut/register! [:state-dir] "/tmp/test")
      (should (sut/registered? [:state-dir])))

    (it "returns false for an unrelated key"
      (sut/register! [:state-dir] "/tmp/test")
      (should-not (sut/registered? [:server])))

    (it "returns true for a nested path"
      (sut/register! [:sessions :store] ::store)
      (should (sut/registered? [:sessions :store])))

    (it "returns false for an absent nested key"
      (sut/register! [:sessions :store] ::store)
      (should-not (sut/registered? [:sessions :naming-strategy]))))

  ;; endregion ^^^^^ registered? ^^^^^

  ;; region ----- -with-nexus isolation -----

  (describe "-with-nexus"

    (it "provides an empty system when initialized with {}"
      (sut/-with-nexus {}
        (should-be-nil (sut/get :state-dir))))

    (it "provides an pre-populated system"
      (sut/-with-nexus {:state-dir "/preset"}
        (should= "/preset" (sut/get :state-dir))))

    (it "isolates mutations from the outer scope"
      (sut/register! [:state-dir] "/outer")
      (sut/-with-nexus {}
        (sut/register! [:state-dir] "/inner"))
      (should= "/outer" (sut/get :state-dir)))

    (it "inner scope does not see outer registrations"
      (sut/register! [:state-dir] "/outer")
      (sut/-with-nexus {}
        (should-be-nil (sut/get :state-dir))))

    (it "is visible to new threads created inside the scope"
      (let [seen (promise)]
        (sut/-with-nexus {:state-dir "/thread-visible"}
          (.start (Thread. #(deliver seen (sut/get :state-dir))))
          (should= "/thread-visible" (deref seen 1000 ::timeout))))))

  (describe "bound-runtime-fn"

    (it "captures the current runtime for later thread execution"
      (let [captured (sut/-with-nexus {:state-dir "/captured"}
                       (sut/bound-runtime-fn (fn [] (sut/get :state-dir))))
            seen     (promise)]
        (sut/reset!)
        (.start (Thread. #(deliver seen (captured))))
        (should= "/captured" (deref seen 1000 ::timeout)))))

  ;; endregion ^^^^^ -with-nexus isolation ^^^^^

  ;; region ----- init! -----

  (describe "init!"

    (it "registers default atoms for config and tool-registry"
      (sut/init!)
      (should (instance? clojure.lang.Atom (sut/get :config)))
      (should (instance? clojure.lang.Atom (sut/get :tool-registry))))

    (it "accepts explicit atom overrides"
      (let [cfg*   (atom {:crew {}})
            tools* (atom {"read" {:name "read"}})]
        (sut/init! {:config cfg* :tool-registry tools*})
        (should= cfg* (sut/get :config))
        (should= tools* (sut/get :tool-registry)))))

  ;; endregion ^^^^^ init! ^^^^^

  ;; region ----- schema / unknown-key warnings -----

  (describe "schema validation"

    (it "does not warn for a known schema key"
      (sut/register! [:state-dir] "/tmp/test")
      (should= [] (filter #(= :nexus/unknown-key (:event %)) (log/get-entries))))

    (it "does not warn for any of the known schema keys"
      (doseq [k [:server :sessions :config :tool-registry
                 :slash-registry :comm-registry :provider-registry
                 :module-index]]
        (sut/register! [k] :anything))
      (should= [] (filter #(= :nexus/unknown-key (:event %)) (log/get-entries))))

    (it "logs a warning for an unknown unnamespaced key"
      (sut/register! [:mystery-slot] 42)
      (let [warnings (filter #(= :nexus/unknown-key (:event %)) (log/get-entries))]
        (should= 1 (count warnings))
        (should= :mystery-slot (:key (first warnings)))))

    (it "does not warn for a namespaced key (module extension point)"
      (sut/register! [:my-module/state] {:active true})
      (should= [] (filter #(= :nexus/unknown-key (:event %)) (log/get-entries)))))

  ;; endregion ^^^^^ schema / unknown-key warnings ^^^^^

  ;; region ----- schema structure -----

  (describe "schema"

    (it "is a map with :name :nexus"
      (should= :nexus (:name sut/schema)))

    (it "declares all expected keys"
      (let [ks (set (keys (:schema sut/schema)))]
        (should (contains? ks :state-dir))
        (should (contains? ks :server))
        (should (contains? ks :sessions))
        (should (contains? ks :config))
        (should (contains? ks :tool-registry))
        (should (contains? ks :slash-registry))
        (should (contains? ks :comm-registry))
        (should (contains? ks :provider-registry))
        (should-not (contains? ks :active-turns))
        (should (contains? ks :module-index)))))

  ;; endregion ^^^^^ schema structure ^^^^^

  )
