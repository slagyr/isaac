(ns isaac.module.loader-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.cli :as cli-registry]
    [isaac.server.status]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.hooks]
    [isaac.logger :as log]
    [isaac.module.manifest]
    [isaac.module.loader :as sut]
    [isaac.server.routes]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(def ctx {:root "/state/.isaac" :cwd "/workspace"})

(defn- mod-dir! [path]
  (fs/mkdirs (nexus/get :fs) path))

(defn- mod-manifest! [path content]
  (let [fs* (nexus/get :fs)]
    (fs/mkdirs fs* (fs/parent path))
    (fs/spit   fs* path content)))

(defn- mod-deps! [path]
  (let [fs* (nexus/get :fs)]
    (fs/mkdirs fs* (fs/parent path))
    (fs/spit   fs* path "{:paths [\"src\" \"resources\"]}")))

(defn- mod-root [id]
  (str "/state/.isaac/modules/" (name id)))

(defn- mod-coord [id]
  {:local/root (mod-root id)})

(defn- write-local-module! [id manifest]
  (let [root (mod-root id)]
    (mod-dir! root)
    (mod-deps! (str root "/deps.edn"))
    (mod-manifest! (str root "/resources/isaac-manifest.edn") (pr-str manifest))))

(defn- local-manifest-path [id]
  (let [root           (mod-root id)
        resources-path (str root "/resources/isaac-manifest.edn")
        src-path       (str root "/src/isaac-manifest.edn")]
    (cond
      (fs/exists? (nexus/get :fs) resources-path) resources-path
      (fs/exists? (nexus/get :fs) src-path) src-path
      :else nil)))

(defn- discover-local! [ids]
  (with-redefs [isaac.module.loader/add-module-deps! (fn [_ _])
                isaac.module.loader/manifest-resource local-manifest-path]
    (sut/discover! {:modules (into {} (map (fn [id] [id (mod-coord id)]) ids))} ctx)))

(defn- unload-telly! []
  (when-let [ns-obj (find-ns 'isaac.comm.telly)]
    (remove-ns (ns-name ns-obj)))
  ;; `remove-ns` does not clear require bookkeeping, so JVM specs can skip
  ;; reloading the module and miss load-time failures on later examples.
  (let [loaded-libs (var-get #'clojure.core/*loaded-libs*)]
    (dosync (alter loaded-libs disj 'isaac.comm.telly))))

(defn- reset-comm-registry! []
  (reset! comm-registry/*registry* (comm-registry/fresh-registry)))

(defn- reset-cli-registry! []
  (cli-registry/clear-module-commands!))

;; ----- process-manifest-berths! helpers -----
;; The loader's `resolve-symbol!` is `requiring-resolve`, so test
;; factories need to be real namespaced fns. These live at the spec
;; namespace's top level so symbols like
;; isaac.module.loader-spec/record-route! resolve cleanly during tests.

(def ^:dynamic *factory-calls* nil)

(defn record-route!
  "Test factory: records the contribution entry into a per-example atom
   and registers it in the nexus at [::test-berth [<method> <path>]]
   so the spec can also assert the nexus side effect."
  [{:keys [method path handler] :as entry}]
  (when *factory-calls* (swap! *factory-calls* conj entry))
  (when (and method path)
    (nexus/register! [::test-berth [method path]] handler)))

(defn- berth-decl-with-factory [factory-sym]
  {:description "test berth"
   :manifest    {:schema {:type :seq
                           :spec {:type    :map
                                  :factory factory-sym
                                  :schema  {:method  {:type :keyword}
                                            :path    {:type :string}
                                            :handler {:type :symbol}}}}}})

(defn- index-with-berth+contributions
  "Build a module-index where `:provider` declares a berth with a
   per-entry factory and each consumer in `consumers` contributes the
   listed routes."
  [berth-id factory-sym consumers]
  (reduce-kv
    (fn [acc consumer-id routes]
      (assoc acc consumer-id {:manifest {berth-id (vec routes)}}))
    {:provider {:manifest {:berths {berth-id (berth-decl-with-factory factory-sym)}}}}
    consumers))

(def valid-comm-manifest
  ;; Phase 8 (isaac-qqgv): comm contributions live at :isaac.server/comm.
  {:id                :isaac.comm.pigeon
   :version           "0.1.0"
   :isaac.server/comm {:pigeon {:factory 'isaac.comm.pigeon/make}}})

(describe "module loader"

  (describe "core-index"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example] (nexus/-with-nested-nexus {:fs (fs/mem-fs)} (example)))

    (before
      (sut/clear-caches!))

    (after
      (sut/clear-caches!))

    (it "reads the core manifest only once"
      (let [resource-calls (atom 0)
            read-calls     (atom 0)]
        (with-redefs-fn {#'isaac.module.loader/manifest-resource (fn [_]
                                                                   (swap! resource-calls inc)
                                                                   :core-resource)
                         #'isaac.module.manifest/read-manifest    (fn [_ _]
                                                                    (swap! read-calls inc)
                                                                    {:id :isaac.core :version "1.0.0"})}
          #(do
             (should= {:isaac.core {:coord {}
                                    :manifest {:id :isaac.core :version "1.0.0"}
                                    :path nil}}
                      (sut/core-index))
             (should= (sut/core-index) (sut/core-index))))
        (should= 1 @resource-calls)
        (should= 1 @read-calls))))

  (describe "discover!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (example)
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})))

    (it "includes the core manifest even when :modules is absent"
      (let [{:keys [index errors]} (sut/discover! {} ctx)]
        (should= [] errors)
        (should= :isaac.core (get-in index [:isaac.core :manifest :id]))))

    (it "builds an index entry for a valid local module"
      (write-local-module! :isaac.comm.pigeon valid-comm-manifest)
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= [] errors)
        (should= :isaac.comm.pigeon (get-in index [:isaac.comm.pigeon :manifest :id]))
        (should= (mod-root :isaac.comm.pigeon) (get-in index [:isaac.comm.pigeon :path]))))

    (it "discovers local/root manifests via classpath loading"
      (let [cwd         (System/getProperty "user.dir")
            module-root "modules/isaac.comm.telly"
            result      (sut/discover! {:modules {:isaac.comm.telly {:local/root module-root}}}
                                      (assoc ctx :cwd cwd))]
        (should= [] (:errors result))
        (should= :isaac.comm.telly (get-in result [:index :isaac.comm.telly :manifest :id]))
        (should= module-root (get-in result [:index :isaac.comm.telly :path]))))

    (it "adds an error when a local/root path is not found"
      (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.ghost {:local/root "/state/.isaac/modules/isaac.comm.ghost"}}} ctx)]
        (should= nil (get index :isaac.comm.ghost))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "local/root path does not resolve" (:value (first errors)))))

    (it "adds an error when a local/root path has no matching manifest on its classpath"
      (mod-dir! (mod-root :isaac.comm.ghost))
      (mod-deps! (str (mod-root :isaac.comm.ghost) "/deps.edn"))
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.ghost])]
        (should= nil (get index :isaac.comm.ghost))
        (should= "modules[\"isaac.comm.ghost\"]" (:key (first errors)))
        (should= "manifest: could not read" (:value (first errors)))))

    (it "reads a local/root manifest directly when no deps.edn is present"
      (let [root (mod-root :isaac.comm.broken)]
        (mod-dir! root)
        (mod-manifest! (str root "/resources/isaac-manifest.edn") (pr-str {:id :isaac.comm.broken :version "0.1.0"}))
        (let [calls (atom [])]
          (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                               (swap! calls conj [id coord]))]
            (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.broken {:local/root root}}} ctx)]
              (should= [] errors)
              (should= :isaac.comm.broken (get-in index [:isaac.comm.broken :manifest :id]))
              (should= [] @calls))))))

    (it "uses the installed runtime fs for local manifest discovery"
      (let [mem  (fs/mem-fs)
            root (mod-root :isaac.comm.runtime)]
        (fs/mkdirs mem root)
        (fs/mkdirs mem (str root "/resources"))
        (fs/spit mem (str root "/resources/isaac-manifest.edn") (pr-str {:id :isaac.comm.runtime :version "0.1.0"}))
        (nexus/-with-nexus {:fs mem}
          (let [{:keys [index errors]} (sut/discover! {:modules {:isaac.comm.runtime {:local/root root}}} ctx)]
            (should= [] errors)
            (should= :isaac.comm.runtime (get-in index [:isaac.comm.runtime :manifest :id]))))))

    (it "adds module deps only once per coordinate across repeated discovery"
      (write-local-module! :isaac.comm.pigeon valid-comm-manifest)
      (let [calls            (atom [])
            classpath-ready? (atom false)]
        (with-redefs [isaac.module.loader/manifest-resource (fn [id]
                                                              (when (and @classpath-ready?
                                                                         (= id :isaac.comm.pigeon))
                                                                (str (mod-root :isaac.comm.pigeon) "/resources/isaac-manifest.edn")))
                      isaac.module.loader/add-module-deps!   (fn [id coord]
                                                              (swap! calls conj [id coord])
                                                              (reset! classpath-ready? true))]
          (let [first-result  (sut/discover! {:modules {:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)}} ctx)
                second-result (sut/discover! {:modules {:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)}} ctx)]
            (should= [] (:errors first-result))
            (should= [] (:errors second-result))
            (should= [[:isaac.comm.pigeon (mod-coord :isaac.comm.pigeon)]] @calls)))))

    (it "adds errors when a manifest fails schema validation"
      (write-local-module! :isaac.comm.pigeon {:id :isaac.comm.pigeon})
      (let [{:keys [index errors]} (discover-local! [:isaac.comm.pigeon])]
        (should= nil (get index :isaac.comm.pigeon))
        (should (some #(= "module-index[\"isaac.comm.pigeon\"].version" (:key %)) errors))))

    (it "builds an index entry for two independent modules"
      (write-local-module! :mod.a {:id :mod.a :version "1"})
      (write-local-module! :mod.b {:id :mod.b :version "1"})
      (let [{:keys [index errors]} (discover-local! [:mod.a :mod.b])]
        (should= [] errors)
        (should= #{:mod.a :mod.b :isaac.core} (set (keys index))))))

  (describe "process-manifest-berths!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
        (binding [*factory-calls* (atom [])]
          (example))))

    (it "invokes the entry-level factory once per contribution entry"
      (let [module-index (index-with-berth+contributions
                           :provider/routes
                           'isaac.module.loader-spec/record-route!
                           {:consumer-a [{:method :get  :path "/a" :handler 'consumer-a/a-handler}]
                            :consumer-b [{:method :post :path "/b" :handler 'consumer-b/b-handler}
                                         {:method :put  :path "/c" :handler 'consumer-b/c-handler}]})]
        (should= [] (sut/process-manifest-berths! module-index))
        (should= 3 (count @*factory-calls*))
        (should= #{:get :post :put} (set (map :method @*factory-calls*)))))

    (it "writes each entry's registration into the ambient nexus"
      (let [module-index (index-with-berth+contributions
                           :provider/routes
                           'isaac.module.loader-spec/record-route!
                           {:consumer-a [{:method :get :path "/a" :handler 'consumer-a/a-handler}]})]
        (sut/process-manifest-berths! module-index)
        (should= 'consumer-a/a-handler (nexus/get-in [::test-berth [:get "/a"]]))))

    (it "skips berths whose schema declares no entry-level :factory"
      (let [module-index {:provider {:manifest {:berths {:provider/silent
                                                          {:description "no factory"
                                                           :manifest    {:schema {:type :seq
                                                                                   :spec {:type :map}}}}}}}
                          :consumer {:manifest {:provider/silent [{:k :v}]}}}]
        (should= [] (sut/process-manifest-berths! module-index))
        (should= [] @*factory-calls*)))

    (it "skips berths that also declare a :config slot (not manifest-only)"
      (let [module-index (-> (index-with-berth+contributions
                               :provider/routes
                               'isaac.module.loader-spec/record-route!
                               {:consumer-a [{:method :get :path "/a"}]})
                             (assoc-in [:provider :manifest :berths :provider/routes :config]
                                       {:path [:routes]}))]
        (should= [] (sut/process-manifest-berths! module-index))
        (should= [] @*factory-calls*)))

    (it "returns an error row when the factory symbol cannot be resolved"
      (let [module-index (index-with-berth+contributions
                           :provider/routes
                           'isaac.module.loader-spec.nope/missing-factory!
                           {:consumer-a [{:method :get :path "/a"}]})
            errors       (sut/process-manifest-berths! module-index)]
        (should= 1 (count errors))
        (should= "module-index.berths[:provider/routes].factory"
                 (:key (first errors)))
        (should= "could not resolve factory symbol: isaac.module.loader-spec.nope/missing-factory!"
                 (:value (first errors)))
        (should= [] @*factory-calls*))))

  (describe "activate!"

    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (around [example]
      (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset-comm-registry!)
        (reset-cli-registry!)
        (sut/clear-activations!)
        (reset! c3env/-overrides {})
        (unload-telly!)
        (example)
        (reset! @#'isaac.module.loader/loaded-module-coords* #{})
        (reset! c3env/-overrides {})
        (sut/clear-activations!)
        (reset-comm-registry!)
        (reset-cli-registry!)
        (unload-telly!)))

    (it "logs activation once"
      ;; Phase 8 (isaac-qqgv): comm factory registration moved into
      ;; the :isaac.server/comm berth's per-entry factory; activate!
      ;; only logs the activation now. Coverage for the registration
      ;; itself lives under process-manifest-berths! and the comm
      ;; registry spec.
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir
                                             :manifest {:isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}]
        (log/capture-logs
          (sut/activate! :isaac.comm.telly module-index)
          (sut/activate! :isaac.comm.telly module-index)
          (let [events (filter #(= :module/activated (:event %)) @log/captured-logs)]
            (should= 1 (count events))
            (should= "isaac.comm.telly" (:module (first events)))))))

    (it "wraps bootstrap namespace load failures in structured error data and logs them"
      ;; Phase 8 of brth (isaac-qqgv): activate! no longer eagerly
      ;; resolves :comm factory symbols (those flow through the
      ;; :isaac.server/comm berth's per-entry factory). The remaining
      ;; activate!-side failure path is :bootstrap symbol resolution.
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:dir telly-dir
                                             :manifest {:bootstrap         'isaac.comm.telly/bootstrap-load
                                                        :isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}]
        (c3env/override! "ISAAC_TELLY_FAIL_ON_LOAD" "true")
        (log/capture-logs
          (let [error (try
                        (sut/activate! :isaac.comm.telly module-index)
                        (catch clojure.lang.ExceptionInfo e
                          e))
                event (first (filter #(= :module/activation-failed (:event %)) @log/captured-logs))]
            (should= :module/activation-failed (:type (ex-data error)))
            (should= :isaac.comm.telly (:module-id (ex-data error)))
            (should-not-be-nil event)
            (should= "isaac.comm.telly" (:module event))))))

    (it "adds local/root deps on first activation"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
            (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls))))

    ;; Phase 5 of the berth epic (isaac-8v1n): route registration moved
    ;; out of activate! entirely. The :isaac.server/route and
    ;; :isaac.server/route-prefix berths flow through
    ;; process-manifest-berths! (covered in that describe block above),
    ;; and the per-entry factory (isaac.server.routes/register-route-entry!)
    ;; is a thin shim around register-route!. The activate!-side tests
    ;; that lived here are gone with the dispatch they tested.

    ;; activate! used to register manifest :cli entries via
    ;; register-cli-extension!. Phase 4 of the berth epic moved :cli
    ;; into the berth pass (process-manifest-berths!), so the
    ;; activate!-side handling is gone. Coverage for the new path
    ;; lives under the "process-manifest-berths!" describe above.

    (it "does not add the same local/root deps twice across activation resets"
      (let [telly-dir    (str (System/getProperty "user.dir") "/modules/isaac.comm.telly-cache-test")
            module-index {:isaac.comm.telly {:coord {:local/root telly-dir}
                                             :path  telly-dir
                                             :manifest {:isaac.server/comm {:telly {:factory 'isaac.comm.telly/make}}}}}
            calls       (atom [])]
        (with-redefs [isaac.module.loader/add-module-deps! (fn [id coord]
                                                             (swap! calls conj [id coord]))]
          (sut/activate! :isaac.comm.telly module-index)
          (sut/clear-activations!)
          (sut/activate! :isaac.comm.telly module-index)
          (should= [[:isaac.comm.telly {:local/root telly-dir}]] @calls)))))

  (describe "comm-kinds"

    (it "returns empty when module index has no comm entries"
      (should= [] (sut/comm-kinds {})))

    (it "returns sorted comm kind name from a module"
      (let [index {:my.mod {:manifest {:isaac.server/comm {:telly {:factory 'foo/make}}}}}]
        (should= ["telly"] (sut/comm-kinds index))))

    (it "filters out entries with :configurable? false"
      (let [index {:my.mod {:manifest {:isaac.server/comm {:internal {:factory 'foo/make :configurable? false}
                                              :external {:factory 'bar/make}}}}}]
        (should= ["external"] (sut/comm-kinds index))))

    (it "aggregates and sorts kinds from multiple modules"
      (let [index {:mod-a {:manifest {:isaac.server/comm {:bravo {:factory 'a/make}}}}
                   :mod-b {:manifest {:isaac.server/comm {:alpha {:factory 'b/make}}}}}]
        (should= ["alpha" "bravo"] (sut/comm-kinds index))))

    (it "with no args falls back to core-index"
      (let [index {:isaac.core {:coord {} :manifest {:id :isaac.core :version "1"
                                                      :isaac.server/comm {:widget {:factory 'foo/make}}}}}]
        (binding [sut/*core-index-override* index]
          (should= ["widget"] (sut/comm-kinds)))))))
